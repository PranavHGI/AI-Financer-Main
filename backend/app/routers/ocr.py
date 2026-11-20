import io
import re
import time
import logging
from typing import Dict, Any
from datetime import datetime
from fastapi import APIRouter, File, UploadFile, Depends, HTTPException, status
from PIL import Image, ImageOps
import pytesseract
from app.database import get_db
from app.models.user import User
from app.routers.auth import get_current_user

router = APIRouter(prefix="/transactions", tags=["OCR"])
logger = logging.getLogger(__name__)

# Basic keywords categorizer
CATEGORY_KEYWORDS = {
    "Food & dining": ["food", "cafe", "coffee", "restaurant", "mcdonald", "burger", "pizza", "dining", "starbucks", "bakery", "hotel", "swiggy", "zomato", "kitchen", "biryani", "dhaba", "bistro", "tea", "chai"],
    "Travel": ["uber", "ola", "cab", "taxi", "train", "flight", "fuel", "petrol", "metro", "bus", "travel", "auto", "diesel", "toll", "parking", "irctc", "indigo", "air"],
    "Shopping": ["shopping", "store", "mall", "mart", "amazon", "flipkart", "clothing", "apparel", "grocery", "supermarket", "retail", "fashion", "bazaar", "myntra", "meesho"],
    "Bills": ["electricity", "water", "bill", "invoice", "power", "gas", "recharge", "telecom", "broadband", "rent", "bescom", "airtel", "jio", "vi", "tatasky"],
    "Entertainment": ["movie", "cinema", "netflix", "theatre", "game", "show", "concert", "booking", "pvr", "inox", "bookmyshow", "hotstar", "spotify"],
    "Health": ["medical", "pharmacy", "doctor", "clinic", "hospital", "medicine", "health", "lab", "apollo", "medplus", "diagnostic"]
}

SKIP_FULL_HEADERS = {
    "invoice", "tax invoice", "retail invoice", "receipt", "cash memo", "cash receipt",
    "bill", "bill receipt", "welcome", "welcome to", "thank you", "thank you visit again",
    "customer copy", "merchant copy", "original copy", "duplicate copy"
}

SKIP_LINE_PREFIXES = [
    "tel", "phone", "mob", "date", "time", "street", "road", "ave", "address",
    "gstin", "order", "customer", "table", "token", "pos", "terminal", "fssai",
    "fare", "total", "subtotal", "sub total", "item", "qty", "rate", "amount", "cash paid", "change"
]

def parse_ocr_text(text: str) -> Dict[str, Any]:
    lines = [line.strip() for line in text.split("\n") if line.strip()]
    
    # 1. Extract Merchant Name
    # Pick the first descriptive line that is not a generic header or metadata
    merchant = "Unknown Merchant"
    for line in lines[:6]:
        clean_line = line.lower().strip()
        # Skip pure generic headers (e.g. "TAX INVOICE", "CASH MEMO")
        if clean_line in SKIP_FULL_HEADERS:
            continue
        # Skip metadata prefixes (e.g. "GSTIN:", "Tel:", "Date:")
        if any(clean_line.startswith(p) for p in SKIP_LINE_PREFIXES):
            continue
        # Skip lines that are purely numeric or long number codes
        if re.search(r'^\d+$', line) or re.search(r'\d{5,}', line):
            continue
        # Strip leading/trailing punctuation
        candidate = re.sub(r'^[#\-*:\s]+|[#\-*:\s]+$', '', line)
        if len(candidate) > 2:
            merchant = candidate
            break

    # 2. Extract Date
    # Return timestamp in seconds
    date_sec = int(time.time())
    date_patterns = [
        (r'(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})', ["%d-%m-%Y", "%d/%m/%Y", "%d-%m-%y", "%d/%m/%y"]),
        (r'(\d{4})[-/](\d{1,2})[-/](\d{1,2})', ["%Y-%m-%d", "%Y/%m/%d"])
    ]
    for pattern, fmts in date_patterns:
        match = re.search(pattern, text)
        if match:
            date_str = match.group(0)
            for fmt in fmts:
                try:
                    dt = datetime.strptime(date_str, fmt)
                    # Sanity check: between year 2020 and 2030
                    if 2020 <= dt.year <= 2030:
                        date_sec = int(dt.timestamp())
                        break
                except ValueError:
                    continue
            break

    # 3. Extract Amount
    # Comprehensive support for: TOTAL, GRAND TOTAL, NET PAYABLE, BILL AMOUNT, RS, INR, ₹, $
    amount = 0.0
    candidates = []

    # Priority 1: Direct Total labels followed by optional currency and amount
    # Handles: "TOTAL: 300.00", "GRAND TOTAL: Rs. 450", "NET PAYABLE: ₹ 1,250.50", "Total: 150.00"
    p1 = r'(?i)(?:grand\s+total|net\s+payable|net\s+amount|bill\s+total|total\s+amount|total\s+due|sub\s+total|total\s+paid|total|amount|due|sum|net)\s*[:=\-]?\s*(?:rs\.?|inr|₹|\$)?\s*([0-9,]+(?:\.[0-9]{1,2})?)'
    for m in re.finditer(p1, text):
        candidates.append(m.group(1))

    # Priority 2: Keyword followed by currency label and amount (e.g. "TOTAL RS 450", "TOTAL INR 500.00")
    if not candidates:
        p2 = r'(?i)(?:grand\s+total|net\s+payable|total|paid|amount)\s+(?:rs\.?|inr|₹|\$)\s*([0-9,]+(?:\.[0-9]{1,2})?)'
        for m in re.finditer(p2, text):
            candidates.append(m.group(1))

    # Priority 3: Explicit currency symbols anywhere in text (e.g. "₹450", "Rs. 1,200", "INR 350")
    if not candidates:
        p3 = r'(?i)(?:₹|rs\.?|inr|\$)\s*([0-9,]+(?:\.[0-9]{1,2})?)'
        for m in re.finditer(p3, text):
            candidates.append(m.group(1))

    # Priority 4: Fallback to standalone decimal numbers ending in 2 digits
    if not candidates:
        p4 = r'\b([0-9,]+\.[0-9]{2})\b'
        for m in re.finditer(p4, text):
            candidates.append(m.group(1))

    for raw_val in candidates:
        try:
            clean_val = raw_val.replace(",", "").strip()
            val = float(clean_val)
            # Avoid picking years (e.g. 2024, 2025, 2026) or out-of-range values
            if val in [2023.0, 2024.0, 2025.0, 2026.0, 2027.0]:
                continue
            if 0.0 < val < 500000.0 and val > amount:
                amount = val
        except ValueError:
            continue

    # 4. Determine Category
    category = "Other"
    found = False
    for cat, keywords in CATEGORY_KEYWORDS.items():
        for kw in keywords:
            if re.search(rf'\b{kw}\b', text, re.IGNORECASE):
                category = cat
                found = True
                break
        if found:
            break

    return {
        "amount": amount,
        "merchant": merchant,
        "category": category,
        "date": date_sec
    }

@router.post("/ocr", response_model=Dict[str, Any])
async def perform_ocr(
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user)
):
    # Verify file extension
    ext = file.filename.split(".")[-1].lower() if "." in file.filename else ""
    if ext not in ["jpg", "jpeg", "png", "bmp", "webp"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid image format. Supported formats: JPG, JPEG, PNG, BMP, WEBP."
        )

    try:
        # Load image from file binary
        image_data = await file.read()
        image = Image.open(io.BytesIO(image_data))

        # 1. Automatically transpose orientation according to EXIF
        image = ImageOps.exif_transpose(image) or image

        # 2. Ensure compatible color mode for OCR
        if image.mode not in ("RGB", "L"):
            image = image.convert("RGB")

        # 3. Downscale if image exceeds max dimension to avoid CPU/memory spike
        max_dim = 1280
        if max(image.width, image.height) > max_dim:
            image.thumbnail((max_dim, max_dim), Image.Resampling.LANCZOS)
        
        # 4. Run Tesseract OCR
        ocr_text = pytesseract.image_to_string(image)
        logger.debug(f"Extracted OCR text:\n{ocr_text}")
        
        # 5. Parse text parameters
        result = parse_ocr_text(ocr_text)
        
        # Return parsed receipt details
        return result
    except Exception as e:
        logger.error("OCR execution failed", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to perform OCR parsing: {str(e)}"
        )

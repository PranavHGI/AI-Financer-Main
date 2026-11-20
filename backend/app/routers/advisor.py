import os
import httpx
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import Annotated, List, Optional
from app.database import get_db
from app.routers.auth import get_current_user
from app.models.user import User
from app.models.transaction import Transaction
from app.models.budget import Budget
from app.schemas.advisor import AdvisorChatRequest, AdvisorChatResponse, ChatMessage
from app.schemas.voice import VoiceCommandRequest, VoiceCommandResponse, VoiceActionEntity
from app.ml.ml_classifier import rule_based_categorize
from app.ml.analytics_models import predict_next_month_expense, detect_anomalies
from collections import defaultdict
from uuid import uuid4
import datetime
import time
import re
from app.config import settings

router = APIRouter(prefix="/advisor", tags=["Advisor"])

def parse_amount(amt_str: str) -> float:
    cleaned = amt_str.replace("₹", "").replace("+", "").replace("-", "").replace(",", "").strip()
    try:
        return float(cleaned)
    except:
        return 0.0

def generate_offline_advice(
    query: str,
    username: str,
    total_income: float,
    total_spent: float,
    safe_to_spend: float,
    category_spending: dict,
    budgets: list,
    transactions: list,
    prediction: float,
    anomaly_summary: str,
    reason: str = None
) -> str:
    query_lower = query.lower()
    prefix = "**[AI Advisor]**\n\n" if not reason else f"**[AI Advisor (Analytics Mode)]**\n*({reason})*\n\n"

    # 1. Budget Keywords
    if "budget" in query_lower or "limit" in query_lower:
        overrun_list = []
        ok_list = []
        for b in budgets:
            spent = category_spending.get(b.category, 0.0)
            if spent > b.amount:
                overrun_list.append(f"🔴 **{b.category}**: Spent ₹{spent:,.2f} of ₹{b.amount:,.2f} limit (Over budget by ₹{spent - b.amount:,.2f})")
            else:
                ok_list.append(f"🟢 **{b.category}**: Spent ₹{spent:,.2f} of ₹{b.amount:,.2f} limit (Remaining: ₹{b.amount - spent:,.2f})")

        response = prefix
        if overrun_list:
            response += "🚨 **Alert:** You have exceeded your limits in the following categories:\n" + "\n".join(overrun_list) + "\n\n"
        if ok_list:
            response += "✅ **On Track:** You are within limits for the following categories:\n" + "\n".join(ok_list) + "\n"
        if not budgets:
            response += "No budgets configured yet. Create a budget in the Goals screen to track limits.\n"

    # 2. Spend / Expense Keywords
    elif any(k in query_lower for k in ["spend", "expense", "cost"]):
        response = prefix
        response += f"You have spent a total of **₹{total_spent:,.2f}** this month.\n\n"
        if category_spending:
            response += "Category-wise Breakdown:\n"
            for cat, amt in category_spending.items():
                percentage = (amt / total_spent * 100) if total_spent > 0 else 0
                response += f"- **{cat}**: ₹{amt:,.2f} ({percentage:.1f}% of total)\n"
        else:
            response += "No expenses logged this month.\n"
        if prediction > 0:
            response += f"\n🔮 **Next Month Forecast:** ₹{prediction:,.2f} predicted based on spending trends.\n"

    # 3. Income Keywords
    elif any(k in query_lower for k in ["income", "earn", "salary", "freelance"]):
        response = prefix
        response += f"You have earned a total of **₹{total_income:,.2f}** this month.\n\n"
        income_txs = [tx for tx in transactions if tx.type == "income"]
        if income_txs:
            response += "Income logs:\n"
            for tx in income_txs:
                merchant = tx.merchant or "Income"
                response += f"- **{merchant}** ({tx.category}): {tx.amount}\n"
        else:
            response += "No income logs found for this month.\n"

    # 4. Saving / Tips Keywords
    elif any(k in query_lower for k in ["save", "saving", "tip"]):
        response = prefix
        response += "Here are customized suggestions to increase your savings based on your data:\n\n"
        if total_spent > total_income:
            response += "1. ⚠️ **Immediate Action Required:** Your monthly spending is higher than your income. Trim non-essential categories (like Shopping or Entertainment) immediately.\n"
        else:
            savings_rate = ((total_income - total_spent) / total_income * 100) if total_income > 0 else 0
            response += f"1. 📈 **Savings Rate:** You saved **{savings_rate:.1f}%** of your earnings this month. Aim to maintain this above 20%.\n"

        top_categories = sorted(category_spending.items(), key=lambda x: x[1], reverse=True)
        if top_categories:
            main_cat, main_amt = top_categories[0]
            response += f"2. 🔍 **Highest Expense:** Your top expense category is **{main_cat}** at **₹{main_amt:,.2f}**. Trimming this by 15% would save you **₹{main_amt * 0.15:,.2f}**.\n"
        response += "3. 🎯 **Set Budget Limits:** Setting a strict monthly budget in the Goals screen is the most effective way to control impulsive spending.\n"

    # 5. Default General diagnostics / Cashflow
    else:
        response = prefix
        response += (
            f"Hello {username}! Here is your real-time financial health summary:\n\n"
            f"- 💵 **Total Income:** ₹{total_income:,.2f}\n"
            f"- 💸 **Total Spent:** ₹{total_spent:,.2f}\n"
            f"- 🛡️ **Safe-to-Spend Balance:** ₹{safe_to_spend:,.2f}\n"
            f"- 🔮 **Next Month Forecast:** ₹{prediction:,.2f}\n\n"
        )
        if total_spent > total_income:
            response += "⚠️ **Warning:** Your spending exceeds your income this month. Consider cutting down on non-essential expenses.\n\n"
        else:
            response += "✅ **Healthy Cashflow:** You are spending within your income. Keep saving!\n\n"

        overrun_list = []
        for b in budgets:
            spent = category_spending.get(b.category, 0.0)
            if spent > b.amount:
                overrun_list.append(f"**{b.category}** (Spent ₹{spent:,.2f} vs Limit ₹{b.amount:,.2f})")

        if overrun_list:
            response += f"🚨 **Budget Alert:** You exceeded budget in: {', '.join(overrun_list)}.\n"
        else:
            response += "🎯 **Budget Status:** All active category budgets are within limits.\n"

    return response


CANDIDATE_MODELS = [
    "gemini-2.5-flash",
    "gemini-flash-latest",
    "gemini-2.5-flash-lite",
    "gemini-flash-lite-latest",
    "gemini-2.5-pro",
    "gemini-pro-latest"
]


@router.post("/chat", response_model=AdvisorChatResponse)
async def chat_with_advisor(
    request: AdvisorChatRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    # Fetch live transaction history and budget configurations for this user
    transactions = db.query(Transaction).filter(Transaction.user_id == current_user.id).all()
    budgets = db.query(Budget).filter(Budget.user_id == current_user.id).all()

    # Calculate financial aggregates
    total_income = 0.0
    total_spent = 0.0
    category_spending = {}
    monthly_totals_map = defaultdict(float)

    for tx in transactions:
        if tx.category == "Transfer":
            continue

        amt = parse_amount(tx.amount)
        if tx.type == "income":
            total_income += amt
        else:
            total_spent += amt
            category_spending[tx.category] = category_spending.get(tx.category, 0.0) + amt
            
            # Extract month for regression forecasting with timestamp normalization
            try:
                ts = tx.timestamp / 1000.0 if tx.timestamp > 1e11 else tx.timestamp
                tx_date = datetime.datetime.utcfromtimestamp(ts)
                month_key = f"{tx_date.year}-{tx_date.month:02d}"
                monthly_totals_map[month_key] += amt
            except Exception:
                pass

    safe_to_spend = total_income - total_spent

    # Calculate expense prediction
    sorted_monthly_keys = sorted(monthly_totals_map.keys())
    historical_spends = [monthly_totals_map[key] for key in sorted_monthly_keys]
    prediction = predict_next_month_expense(historical_spends)

    # Detect anomaly spending outliers (Isolation Forest)
    expenses = [tx for tx in transactions if tx.type == "expense"]
    anomaly_list = []
    if len(expenses) >= 5:
        amounts = [parse_amount(tx.amount) for tx in expenses]
        flags = detect_anomalies(amounts)
        for idx, flag in enumerate(flags):
            if flag == -1:
                tx = expenses[idx]
                anomaly_list.append(f"- Unusual transaction: ₹{amounts[idx]:.2f} spent at {tx.merchant or 'Unknown'} in category {tx.category}")
    anomaly_summary = "\n".join(anomaly_list) if anomaly_list else "No unusual spending anomalies detected."

    # Budget limit vs spending breakdown
    budget_details = []
    for b in budgets:
        spent = category_spending.get(b.category, 0.0)
        status_label = "OK"
        if spent > b.amount:
            status_label = "OVER BUDGET"
        budget_details.append(
            f"- {b.category}: Spent ₹{spent:.2f} of ₹{b.amount:.2f} Limit ({status_label})"
        )
    budget_summary = "\n".join(budget_details) if budget_details else "No specific budget limits configured."

    # Recent transactions listing
    recent_txs = sorted(transactions, key=lambda x: x.timestamp, reverse=True)[:5]
    recent_details = []
    for tx in recent_txs:
        recent_details.append(
            f"- {tx.merchant} ({tx.category}): {tx.type.upper()} {tx.amount} on Source: {tx.source}"
        )
    recent_summary = "\n".join(recent_details) if recent_details else "No recent transactions found."

    # Build system instructions
    system_prompt = (
        f"You are an expert personal financial advisor and budgeting coach named AI Advisor, designed to assist {current_user.username}.\n"
        f"Here is the user's live financial data for the current month:\n"
        f"- Total Income: ₹{total_income:.2f}\n"
        f"- Total Expenses: ₹{total_spent:.2f}\n"
        f"- Remaining Balance (Safe-to-Spend): ₹{safe_to_spend:.2f}\n\n"
        f"ML Analytics Predictions:\n"
        f"- Next Month Forecast Expense (Linear Regression): ₹{prediction:.2f}\n"
        f"- Detected Unusual Spending (Outliers):\n{anomaly_summary}\n\n"
        f"Monthly Budgets Configurations:\n{budget_summary}\n\n"
        f"Recent Transactions:\n{recent_summary}\n\n"
        f"Provide personalized, constructive, and actionable budgeting advice based strictly on local cashflow and goals. "
        f"You are strictly prohibited from offering regulated investment advice, stock or cryptocurrency trading recommendations, tax filings legal counsel, or guaranteeing any future financial returns or outcomes. "
        f"Format currency in Indian Rupees (₹). Keep answers structured, encouraging, and easy to read. "
        f"Be conversational but brief, avoiding long generic essays. Maintain a pure budgeting advisor focus."
    )

    api_key = settings.GEMINI_API_KEY or os.getenv("GEMINI_API_KEY")

    if not api_key:
        return AdvisorChatResponse(
            response=generate_offline_advice(
                query=request.message,
                username=current_user.username,
                total_income=total_income,
                total_spent=total_spent,
                safe_to_spend=safe_to_spend,
                category_spending=category_spending,
                budgets=budgets,
                transactions=transactions,
                prediction=prediction,
                anomaly_summary=anomaly_summary,
                reason="GEMINI_API_KEY is not configured on the server."
            )
        )

    # Format history and request payload for Gemini API
    contents = []
    for msg in request.history:
        contents.append({
            "role": msg.role,
            "parts": [{"text": msg.content}]
        })

    # Append current user query
    contents.append({
        "role": "user",
        "parts": [{"text": request.message}]
    })

    payload = {
        "systemInstruction": {
            "parts": [{"text": system_prompt}]
        },
        "contents": contents
    }

    headers = {
        "x-goog-api-key": api_key,
        "Content-Type": "application/json"
    }

    # Attempt calling models in order of priority; fall back on high-demand 503 or 429
    try:
        async with httpx.AsyncClient(timeout=25.0) as client:
            for model_name in CANDIDATE_MODELS:
                url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent"
                try:
                    res = await client.post(url, json=payload, headers=headers)
                    if res.status_code == 200:
                        data = res.json()
                        candidates = data.get("candidates", [])
                        if candidates and "content" in candidates[0]:
                            parts = candidates[0]["content"].get("parts", [])
                            if parts and "text" in parts[0]:
                                return AdvisorChatResponse(response=parts[0]["text"])
                except Exception:
                    continue
    except Exception:
        pass

    # Graceful fallback: If Gemini models are experiencing high demand / downtime,
    # return the rich rule-based local financial analysis so the user never sees an error.
    return AdvisorChatResponse(
        response=generate_offline_advice(
            query=request.message,
            username=current_user.username,
            total_income=total_income,
            total_spent=total_spent,
            safe_to_spend=safe_to_spend,
            category_spending=category_spending,
            budgets=budgets,
            transactions=transactions,
            prediction=prediction,
            anomaly_summary=anomaly_summary,
            reason="Live Gemini AI is experiencing high demand; generated from live transactions."
        )
    )


# ---------------------------------------------------------------------------
# AI Voice Assistant Intent Engine & Command Router (Part 1)
# ---------------------------------------------------------------------------

CATEGORIES_LIST = [
    "Food & dining", "Travel", "Shopping", "Education", "Bills",
    "Entertainment", "Health", "Salary", "Freelance", "Transfer", "Other"
]

def extract_voice_amount(text: str) -> Optional[float]:
    """Extract numeric transaction amounts from natural language transcripts."""
    # 1. Match '5k' or '2.5k'
    k_match = re.search(r'(?i)\b(\d+(?:\.\d+)?)\s*k\b', text)
    if k_match:
        try:
            return float(k_match.group(1)) * 1000.0
        except ValueError:
            pass

    # 2. Match standard currency expressions (₹, Rs, INR, rupees, bucks, or standalone digits)
    patterns = [
        r'(?i)(?:rs\.?|inr|₹|rupees|bucks)\s*([\d,]+(?:\.\d{1,2})?)',
        r'(?i)(?:spent|paid|for|of|received|amount|cost)\s*(?:of|is)?\s*(?:rs\.?|inr|₹|rupees|bucks)?\s*([\d,]+(?:\.\d{1,2})?)',
        r'\b(\d+(?:,\d+)*(?:\.\d{1,2})?)\s*(?:rs\.?|inr|₹|rupees|bucks)\b',
        r'\b(\d+(?:\.\d{1,2})?)\b'
    ]
    for p in patterns:
        m = re.search(p, text)
        if m:
            clean = m.group(1).replace(",", "").strip()
            val = float(clean)
            if val > 0.0:
                return val
    return None

def extract_voice_merchant(text: str, amount_val: Optional[float] = None) -> str:
    """Extract clean merchant or payee name from spoken query."""
    clean = text.lower()

    # 1. Prioritize recognizable brands
    known_merchants = [
        "Starbucks", "McDonalds", "Swiggy", "Zomato", "KFC", "Dominos", "Subway", "Blinkit", "Zepto", "Instamart",
        "Uber", "Ola", "Airtel", "Jio", "Amazon", "Flipkart", "Myntra", "Zara", "Netflix", "Spotify", "Apollo"
    ]
    for km in known_merchants:
        if re.search(r'(?i)\b' + re.escape(km.lower()) + r'\b', clean):
            return km

    # 2. Strip common leading verbs, numbers, and currency keywords with word boundaries
    if amount_val:
        clean = re.sub(r'(?i)\b' + str(int(amount_val)) + r'\b', '', clean)
        clean = re.sub(r'(?i)\b' + str(amount_val) + r'\b', '', clean)
    clean = re.sub(r'(?i)\b(?:rs\.?|inr|₹|rupees|bucks|spent|paid|bought|received|earned|added)\b', '', clean)

    # 3. Look for preposition indicators (at Starbucks, to Uber, from Amazon, for electricity)
    m = re.search(r'(?i)\b(?:at|to|from|for|on)\s+([a-z0-9\s&\'\-]+?)(?=\s+(?:via|using|from|with|on|in|today|yesterday|\.|$))', clean)
    if m:
        candidate = m.group(1).strip()
        # Filter generic non-merchant words
        if candidate and candidate not in ["coffee", "lunch", "dinner", "groceries", "food", "bills", "myself", "me"]:
            return candidate.title()

    return "Store / Merchant"

def resolve_voice_category(text: str, merchant: str) -> str:
    """Map natural spoken language and merchant to one of 11 standard categories."""
    combined = f"{merchant} {text}".lower()
    
    if any(k in combined for k in ["salary", "paycheck", "wages", "stipend"]):
        return "Salary"
    if any(k in combined for k in ["freelance", "upwork", "fiverr", "client project", "contract"]):
        return "Freelance"
    if any(k in combined for k in ["self transfer", "transfer to", "transferred to self"]):
        return "Transfer"
    if any(k in combined for k in ["uber", "ola", "cab", "taxi", "flight", "petrol", "fuel", "metro", "train", "travel"]):
        return "Travel"
    if any(k in combined for k in ["starbucks", "coffee", "lunch", "dinner", "food", "restaurant", "cafe", "swiggy", "zomato", "burger", "pizza", "kfc"]):
        return "Food & dining"
    if any(k in combined for k in ["electricity", "water bill", "gas", "bill", "recharge", "airtel", "jio", "wifi", "broadband"]):
        return "Bills"
    if any(k in combined for k in ["netflix", "movie", "cinema", "prime", "spotify", "game", "theater", "entertainment"]):
        return "Entertainment"
    if any(k in combined for k in ["hospital", "doctor", "pharmacy", "medicine", "clinic", "health", "dental", "apollo"]):
        return "Health"
    if any(k in combined for k in ["school", "college", "tuition", "course", "udemy", "book", "education", "fees"]):
        return "Education"
    if any(k in combined for k in ["amazon", "flipkart", "shopping", "clothes", "store", "mall", "grocery", "mart", "blinkit", "zepto"]):
        return "Shopping"

    # Use model / keyword helper
    cat = rule_based_categorize(merchant)
    return cat if cat in CATEGORIES_LIST else "Other"

def extract_voice_account(text: str, default_account: str = "Cash") -> str:
    """Detect payment method mentioned in transcript."""
    t = text.lower()
    if any(k in t for k in ["credit card", "credit", "card"]):
        return "Credit Card"
    if any(k in t for k in ["bank", "account", "upi", "gpay", "google pay", "phonepe", "paytm", "debit"]):
        return "Bank Account"
    return default_account or "Cash"


@router.post("/voice-command", response_model=VoiceCommandResponse)
async def process_voice_command(
    request: VoiceCommandRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Session = Depends(get_db)
):
    transcript = request.transcript.strip()
    if not transcript:
        return VoiceCommandResponse(
            action_type="UNKNOWN",
            spoken_text="I did not catch that. Please tap the microphone and speak again.",
            display_text="No audio transcript received."
        )

    clean_t = transcript.lower()

    # 1. INTENT: Query Spending
    if any(clean_t.startswith(q) or f" {q} " in clean_t for q in ["how much did i spend", "what did i spend", "how much spent", "total spend", "spending this month", "my expenses"]):
        txs = db.query(Transaction).filter(Transaction.user_id == current_user.id, Transaction.type == "expense").all()
        target_category = next((c for c in CATEGORIES_LIST if c.lower() in clean_t), None)
        
        if target_category:
            cat_sum = sum(parse_amount(tx.amount) for tx in txs if tx.category == target_category)
            spoken = f"You have spent {cat_sum:,.0f} rupees on {target_category}."
            display = f"Total {target_category} spending: ₹{cat_sum:,.2f}"
            return VoiceCommandResponse(
                action_type="QUERY_SPENDING",
                spoken_text=spoken,
                display_text=display,
                data={"category": target_category, "spent": cat_sum}
            )
        else:
            total_spent = sum(parse_amount(tx.amount) for tx in txs if tx.category != "Transfer")
            spoken = f"You have spent a total of {total_spent:,.0f} rupees across {len(txs)} transactions."
            display = f"Total recorded expenses: ₹{total_spent:,.2f} ({len(txs)} transactions)"
            return VoiceCommandResponse(
                action_type="QUERY_SPENDING",
                spoken_text=spoken,
                display_text=display,
                data={"total_spent": total_spent, "count": len(txs)}
            )

    # 2. INTENT: Query Budget
    if any(k in clean_t for k in ["budget status", "within budget", "budget left", "how much budget"]):
        budgets = db.query(Budget).filter(Budget.user_id == current_user.id).all()
        txs = db.query(Transaction).filter(Transaction.user_id == current_user.id, Transaction.type == "expense").all()
        
        category_spent = defaultdict(float)
        for tx in txs:
            category_spent[tx.category] += parse_amount(tx.amount)

        if not budgets:
            spoken = "You have not configured any category budgets yet. You can set one by speaking or in the Goals tab."
            display = "No category budgets found for this account."
            return VoiceCommandResponse(action_type="QUERY_BUDGET", spoken_text=spoken, display_text=display)

        overrun = []
        for b in budgets:
            s = category_spent[b.category]
            if s > b.amount:
                overrun.append(f"{b.category} by {s - b.amount:,.0f} rupees")

        if overrun:
            spoken = f"Alert: You have exceeded budget in {', '.join(overrun)}."
            display = f"Over-budget categories: {', '.join(overrun)}"
        else:
            spoken = "Great news! You are currently within budget limits for all configured categories."
            display = "All category budgets are healthy and within limits."

        return VoiceCommandResponse(
            action_type="QUERY_BUDGET",
            spoken_text=spoken,
            display_text=display,
            data={"over_budget_count": len(overrun)}
        )

    # 3. INTENT: Set Budget by Voice ("set budget of 5000 for Food")
    if "set budget" in clean_t or "limit of" in clean_t:
        amount_val = extract_voice_amount(clean_t)
        target_category = next((c for c in CATEGORIES_LIST if c.lower() in clean_t), "Other")
        
        if amount_val and amount_val > 0.0:
            existing_b = db.query(Budget).filter(Budget.user_id == current_user.id, Budget.category == target_category).first()
            if existing_b:
                existing_b.amount = amount_val
            else:
                db.add(Budget(id=str(uuid4()), user_id=current_user.id, category=target_category, amount=amount_val))
            db.commit()

            spoken = f"Your monthly budget for {target_category} has been set to {amount_val:,.0f} rupees."
            display = f"Budget for {target_category} updated to ₹{amount_val:,.2f}"
            return VoiceCommandResponse(
                action_type="SET_BUDGET",
                spoken_text=spoken,
                display_text=display,
                data={"category": target_category, "limit": amount_val}
            )

    # 4. INTENT: Log Transaction (Expense or Income)
    amount_val = extract_voice_amount(transcript)
    if amount_val and amount_val > 0.0:
        is_income = any(clean_t.startswith(w) or f" {w} " in clean_t for w in ["earned", "received", "salary", "freelance", "got", "income", "credited"])
        tx_type = "income" if is_income else "expense"
        merchant = extract_voice_merchant(transcript, amount_val)
        category = resolve_voice_category(transcript, merchant)
        account = extract_voice_account(transcript, request.account_preference or "Cash")
        tx_timestamp = request.client_timestamp or time.time()

        # Commit directly to database with source = 'voice'
        new_tx = Transaction(
            id=str(uuid4()),
            user_id=current_user.id,
            type=tx_type,
            amount=f"₹{amount_val:.2f}",
            category=category,
            merchant=merchant,
            timestamp=tx_timestamp,
            source="voice",
            description=f"Logged via Voice: \"{transcript}\"",
            masked_account_ref=account,
            confidence=1.0
        )
        db.add(new_tx)
        db.commit()
        db.refresh(new_tx)

        spoken_amt = f"{amount_val:,.0f}" if amount_val.is_integer() else f"{amount_val:,.2f}"
        if tx_type == "expense":
            spoken = f"Logged {spoken_amt} rupees for {merchant} under {category}."
            display = f"Expense of ₹{amount_val:,.2f} recorded for {merchant} ({category})."
        else:
            spoken = f"Received {spoken_amt} rupees income recorded under {category}."
            display = f"Income of ₹{amount_val:,.2f} from {merchant} recorded under {category}."

        entity = VoiceActionEntity(
            action_type="LOG_TRANSACTION",
            transaction_id=new_tx.id,
            type=tx_type,
            amount=amount_val,
            merchant=merchant,
            category=category,
            account=account,
            notes=new_tx.description,
            executed=True
        )

        return VoiceCommandResponse(
            action_type="LOG_TRANSACTION",
            spoken_text=spoken,
            display_text=display,
            entity=entity,
            data={"transaction_id": new_tx.id, "amount": amount_val, "merchant": merchant, "category": category}
        )

    # 5. INTENT: General Conversational Guidance (Call candidate models or offline fallback)
    advice = generate_offline_advice(
        query=transcript,
        username=current_user.username,
        total_income=0.0,
        total_spent=0.0,
        safe_to_spend=0.0,
        category_spending={},
        budgets=[],
        transactions=[],
        prediction=0.0,
        anomaly_summary="",
        reason="Voice assistant financial advice."
    )
    # Strip markdown headers for spoken voice
    spoken_summary = re.sub(r'[\*#_`]', '', advice).split("\n\n")[0].strip()
    return VoiceCommandResponse(
        action_type="GENERAL_ADVICE",
        spoken_text=spoken_summary[:200] if spoken_summary else "Here is what I found based on your financial patterns.",
        display_text=advice
    )

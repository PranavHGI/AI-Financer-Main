from app.routers.ocr import parse_ocr_text

def test_parse_ocr_food_receipt():
    sample_text = """
    STARBUCKS COFFEE
    Store #12345
    Date: 2026-08-25
    1x Caffe Latte - 180.00
    1x Chocolate Muffin - 120.00
    TOTAL: 300.00
    Cash Paid: 500.00
    Change: 200.00
    Thank you!
    """
    result = parse_ocr_text(sample_text)
    assert result["amount"] == 300.0
    assert "Starbucks" in result["merchant"] or "STARBUCKS" in result["merchant"]
    assert result["category"] == "Food & dining"

def test_parse_ocr_travel_receipt():
    sample_text = """
    UBER RIDE INVOICE
    Trip ID: 9876543
    Fare: 150.00
    Tolls: 0.00
    Total Paid: 150.00
    Payment: Credit Card
    """
    result = parse_ocr_text(sample_text)
    assert result["amount"] == 150.0
    assert "Uber" in result["merchant"] or "UBER" in result["merchant"]
    assert result["category"] == "Travel"

def test_parse_ocr_indian_receipt():
    sample_text = """
    HALDIRAM RESTAURANT
    GSTIN: 29ABCDE1234F1Z5
    Date: 15/11/2026
    1x Thali - 250.00
    2x Gulab Jamun - 100.00
    GRAND TOTAL: Rs. 350.00
    THANK YOU VISIT AGAIN
    """
    result = parse_ocr_text(sample_text)
    assert result["amount"] == 350.0
    assert "Haldiram" in result["merchant"] or "HALDIRAM" in result["merchant"]
    assert result["category"] == "Food & dining"

def test_parse_ocr_whole_number_receipt():
    sample_text = """
    MORE SUPERMARKET
    Cash Receipt
    Date: 12-10-2026
    Items: 3
    NET PAYABLE: ₹ 500
    """
    result = parse_ocr_text(sample_text)
    assert result["amount"] == 500.0
    assert "More" in result["merchant"] or "MORE" in result["merchant"]
    assert result["category"] == "Shopping"

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.main import app
from app.database import Base, get_db
from app.models.category import Category
from app.models.transaction import Transaction
from app.models.budget import Budget
from uuid import uuid4

SQLALCHEMY_DATABASE_URL = "sqlite:///:memory:"

engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base.metadata.create_all(bind=engine)

def override_get_db():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()

app.dependency_overrides[get_db] = override_get_db

client = TestClient(app)

def get_auth_headers(username="voiceuser"):
    client.post(
        "/auth/register",
        json={"username": username, "email": f"{username}@test.com", "password": "password123"},
    )
    login_resp = client.post(
        "/auth/token",
        data={"username": username, "password": "password123"},
    )
    token = login_resp.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}

def test_voice_log_expense_starbucks():
    headers = get_auth_headers("vuser1")
    resp = client.post(
        "/advisor/voice-command",
        headers=headers,
        json={"transcript": "Spent 350 rupees at Starbucks on coffee using my credit card"}
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["action_type"] == "LOG_TRANSACTION"
    assert data["entity"]["amount"] == 350.0
    assert data["entity"]["merchant"] == "Starbucks"
    assert data["entity"]["category"] == "Food & dining"
    assert data["entity"]["account"] == "Credit Card"
    assert data["entity"]["type"] == "expense"
    assert data["entity"]["executed"] is True
    assert "Logged 350 rupees for Starbucks" in data["spoken_text"]

def test_voice_log_income_salary():
    headers = get_auth_headers("vuser2")
    resp = client.post(
        "/advisor/voice-command",
        headers=headers,
        json={"transcript": "Received 50000 salary from employer"}
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["action_type"] == "LOG_TRANSACTION"
    assert data["entity"]["amount"] == 50000.0
    assert data["entity"]["type"] == "income"
    assert data["entity"]["category"] == "Salary"
    assert "Received 50,000 rupees income" in data["spoken_text"]

def test_voice_query_spending():
    headers = get_auth_headers("vuser3")
    # First add an expense
    client.post(
        "/advisor/voice-command",
        headers=headers,
        json={"transcript": "Spent 1200 at Swiggy for dinner"}
    )
    # Query spending
    resp = client.post(
        "/advisor/voice-command",
        headers=headers,
        json={"transcript": "How much did I spend this month?"}
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["action_type"] == "QUERY_SPENDING"
    assert "1,200 rupees" in data["spoken_text"]

def test_voice_set_budget():
    headers = get_auth_headers("vuser4")
    resp = client.post(
        "/advisor/voice-command",
        headers=headers,
        json={"transcript": "Set budget of 6000 for Food & dining"}
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["action_type"] == "SET_BUDGET"
    assert "6,000 rupees" in data["spoken_text"]
    assert data["data"]["limit"] == 6000.0

def test_voice_query_budget():
    headers = get_auth_headers("vuser5")
    # Query before budget set
    resp = client.post(
        "/advisor/voice-command",
        headers=headers,
        json={"transcript": "Am I within budget?"}
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["action_type"] == "QUERY_BUDGET"

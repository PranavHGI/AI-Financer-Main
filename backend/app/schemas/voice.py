from pydantic import BaseModel, Field
from typing import Optional, Dict, Any, List

class VoiceCommandRequest(BaseModel):
    transcript: str = Field(..., description="Transcribed voice command from the user")
    client_timestamp: Optional[float] = Field(None, description="Client Unix timestamp in seconds")
    account_preference: Optional[str] = Field("Cash", description="Preferred default payment method")

class VoiceActionEntity(BaseModel):
    action_type: str = Field(..., description="Action type: LOG_TRANSACTION, QUERY_SPENDING, SET_BUDGET, QUERY_BUDGET, GENERAL_ADVICE")
    transaction_id: Optional[str] = None
    type: Optional[str] = None # "expense" or "income"
    amount: Optional[float] = None
    merchant: Optional[str] = None
    category: Optional[str] = None
    account: Optional[str] = "Cash"
    notes: Optional[str] = None
    executed: bool = False

class VoiceCommandResponse(BaseModel):
    action_type: str
    spoken_text: str
    display_text: str
    entity: Optional[VoiceActionEntity] = None
    data: Optional[Dict[str, Any]] = None

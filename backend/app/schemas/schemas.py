from pydantic import BaseModel
from typing import Optional
from datetime import datetime


class UserCreate(BaseModel):
    email: str
    username: str
    password: str


class UserResponse(BaseModel):
    id: int
    email: str
    username: str
    created_at: datetime

    model_config = {"from_attributes": True}


class Token(BaseModel):
    access_token: str
    token_type: str


class TokenData(BaseModel):
    username: Optional[str] = None


class PlanCreate(BaseModel):
    exercise: str
    calories: float = 0.0
    repeat_count: int = 0
    selected_days: str = ""


class PlanUpdate(BaseModel):
    completed: bool = False
    time_completed: Optional[datetime] = None


class PlanResponse(BaseModel):
    id: int
    exercise: str
    calories: float
    repeat_count: int
    selected_days: str
    completed: bool
    time_completed: Optional[datetime] = None
    created_at: datetime

    model_config = {"from_attributes": True}


class WorkoutResultCreate(BaseModel):
    exercise_name: str
    repeated_count: int = 0
    confidence: float = 0.0
    calorie: float = 0.0
    workout_time_in_min: float = 0.0


class WorkoutResultResponse(BaseModel):
    id: int
    exercise_name: str
    repeated_count: int
    confidence: float
    calorie: float
    workout_time_in_min: float
    timestamp: datetime

    model_config = {"from_attributes": True}


class WorkoutSummary(BaseModel):
    total_workouts: int
    total_reps: int
    total_calories: float
    total_time_min: float
    exercises_breakdown: list[dict]


class DailyStats(BaseModel):
    date: str
    calories: float = 0.0
    reps: int = 0
    workouts: int = 0
    time_min: float = 0.0


class WeeklyStats(BaseModel):
    daily_stats: list[DailyStats]
    total_calories: float
    total_workouts: int
    total_reps: int
    streak: int = 0


class ExerciseAnalytics(BaseModel):
    exercise_name: str
    total_sessions: int
    total_reps: int
    best_reps: int
    avg_confidence: float
    total_calories: float
    last_session: Optional[WorkoutResultResponse] = None

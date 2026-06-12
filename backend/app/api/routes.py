from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.orm import Session
from sqlalchemy import func
from datetime import datetime, timedelta, timezone
from typing import Optional

from app.database import get_db
from app.models.models import User, Plan, WorkoutResult
from app.schemas.schemas import (
    UserCreate, UserResponse, Token,
    PlanCreate, PlanUpdate, PlanResponse,
    WorkoutResultCreate, WorkoutResultResponse, WorkoutSummary,
    WeeklyStats, DailyStats, ExerciseAnalytics,
)
from app.services.auth import (
    get_password_hash, verify_password, create_access_token, get_current_user,
)

router = APIRouter(prefix="/api", tags=["auth"])


@router.post("/auth/register", response_model=UserResponse)
def register(user_data: UserCreate, db: Session = Depends(get_db)):
    existing = db.query(User).filter(
        (User.email == user_data.email) | (User.username == user_data.username)
    ).first()
    if existing:
        raise HTTPException(status_code=400, detail="Email or username already taken")
    user = User(
        email=user_data.email,
        username=user_data.username,
        hashed_password=get_password_hash(user_data.password),
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@router.post("/auth/login", response_model=Token)
def login(data: dict, db: Session = Depends(get_db)):
    username = data.get("username")
    password = data.get("password")
    if not username or not password:
        raise HTTPException(status_code=400, detail="Username and password required")
    user = db.query(User).filter(User.username == username).first()
    if not user or not verify_password(password, user.hashed_password):
        raise HTTPException(status_code=401, detail="Invalid credentials")
    token = create_access_token(data={"sub": user.username})
    return {"access_token": token, "token_type": "bearer"}


@router.get("/auth/me", response_model=UserResponse)
def get_me(current_user: User = Depends(get_current_user)):
    return current_user


plan_router = APIRouter(prefix="/api/plans", tags=["plans"])
workout_router = APIRouter(prefix="/api/workouts", tags=["workouts"])
summary_router = APIRouter(prefix="/api/summary", tags=["summary"])
analytics_router = APIRouter(prefix="/api/analytics", tags=["analytics"])


@plan_router.get("/", response_model=list[PlanResponse])
def list_plans(
    day: Optional[str] = None,
    completed: Optional[bool] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    query = db.query(Plan).filter(Plan.user_id == current_user.id)
    if day:
        query = query.filter(Plan.selected_days.like(f"%{day}%"))
    if completed is not None:
        query = query.filter(Plan.completed == completed)
    return query.order_by(Plan.created_at.desc()).all()


@plan_router.post("/", response_model=PlanResponse, status_code=201)
def create_plan(
    plan_data: PlanCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    plan = Plan(**plan_data.model_dump(), user_id=current_user.id)
    db.add(plan)
    db.commit()
    db.refresh(plan)
    return plan


@plan_router.put("/{plan_id}", response_model=PlanResponse)
def update_plan(
    plan_id: int,
    plan_data: PlanUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    plan = db.query(Plan).filter(Plan.id == plan_id, Plan.user_id == current_user.id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan not found")
    for key, value in plan_data.model_dump(exclude_unset=True).items():
        setattr(plan, key, value)
    db.commit()
    db.refresh(plan)
    return plan


@plan_router.delete("/{plan_id}")
def delete_plan(
    plan_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    plan = db.query(Plan).filter(Plan.id == plan_id, Plan.user_id == current_user.id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan not found")
    db.delete(plan)
    db.commit()
    return {"message": "Plan deleted", "id": plan_id}


@workout_router.get("/", response_model=list[WorkoutResultResponse])
def list_workouts(
    limit: int = Query(50, ge=1, le=500),
    offset: int = Query(0, ge=0),
    exercise: Optional[str] = None,
    from_date: Optional[str] = None,
    to_date: Optional[str] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    query = db.query(WorkoutResult).filter(WorkoutResult.user_id == current_user.id)
    if exercise:
        query = query.filter(WorkoutResult.exercise_name == exercise)
    if from_date:
        query = query.filter(WorkoutResult.timestamp >= datetime.fromisoformat(from_date))
    if to_date:
        query = query.filter(WorkoutResult.timestamp <= datetime.fromisoformat(to_date))
    return query.order_by(WorkoutResult.timestamp.desc()).offset(offset).limit(limit).all()


@workout_router.post("/", response_model=WorkoutResultResponse, status_code=201)
def create_workout(
    workout_data: WorkoutResultCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    result = WorkoutResult(**workout_data.model_dump(), user_id=current_user.id)
    db.add(result)
    db.commit()
    db.refresh(result)
    return result


@workout_router.post("/batch", response_model=list[WorkoutResultResponse])
def create_workouts_batch(
    workouts: list[WorkoutResultCreate],
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    results = [WorkoutResult(**w.model_dump(), user_id=current_user.id) for w in workouts]
    for r in results:
        db.add(r)
    db.commit()
    for r in results:
        db.refresh(r)
    return results


@workout_router.delete("/{workout_id}")
def delete_workout(
    workout_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    result = (
        db.query(WorkoutResult)
        .filter(WorkoutResult.id == workout_id, WorkoutResult.user_id == current_user.id)
        .first()
    )
    if not result:
        raise HTTPException(status_code=404, detail="Workout not found")
    db.delete(result)
    db.commit()
    return {"message": "Workout deleted", "id": workout_id}


@summary_router.get("/", response_model=WorkoutSummary)
def get_summary(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    results = (
        db.query(WorkoutResult)
        .filter(WorkoutResult.user_id == current_user.id)
        .all()
    )
    total_workouts = len(results)
    total_reps = sum(r.repeated_count for r in results)
    total_calories = sum(r.calorie for r in results)
    total_time_min = sum(r.workout_time_in_min for r in results)

    exercise_breakdown = {}
    for r in results:
        if r.exercise_name not in exercise_breakdown:
            exercise_breakdown[r.exercise_name] = {
                "exercise": r.exercise_name,
                "total_reps": 0,
                "total_calories": 0.0,
                "total_time_min": 0.0,
                "count": 0,
                "avg_confidence": 0.0,
            }
        e = exercise_breakdown[r.exercise_name]
        e["total_reps"] += r.repeated_count
        e["total_calories"] += r.calorie
        e["total_time_min"] += r.workout_time_in_min
        e["count"] += 1
        e["avg_confidence"] = (e["avg_confidence"] * (e["count"] - 1) + r.confidence) / e["count"]

    return WorkoutSummary(
        total_workouts=total_workouts,
        total_reps=total_reps,
        total_calories=round(total_calories, 2),
        total_time_min=round(total_time_min, 2),
        exercises_breakdown=list(exercise_breakdown.values()),
    )


@analytics_router.get("/weekly", response_model=WeeklyStats)
def get_weekly_stats(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    week_ago = datetime.now(timezone.utc) - timedelta(days=7)
    results = (
        db.query(WorkoutResult)
        .filter(
            WorkoutResult.user_id == current_user.id,
            WorkoutResult.timestamp >= week_ago,
        )
        .all()
    )

    daily = {}
    for r in results:
        day = r.timestamp.strftime("%Y-%m-%d") if hasattr(r.timestamp, "strftime") else r.timestamp.date().isoformat()
        if day not in daily:
            daily[day] = {"date": day, "calories": 0.0, "reps": 0, "workouts": 0, "time_min": 0.0}
        daily[day]["calories"] += r.calorie
        daily[day]["reps"] += r.repeated_count
        daily[day]["workouts"] += 1
        daily[day]["time_min"] += r.workout_time_in_min

    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    return WeeklyStats(
        daily_stats=[DailyStats(**v) for v in sorted(daily.values(), key=lambda x: x["date"])],
        total_calories=round(sum(d["calories"] for d in daily.values()), 2),
        total_workouts=sum(d["workouts"] for d in daily.values()),
        total_reps=sum(d["reps"] for d in daily.values()),
        streak=calculate_streak(db, current_user.id),
    )


@analytics_router.get("/exercises/{exercise_name}", response_model=ExerciseAnalytics)
def get_exercise_analytics(
    exercise_name: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    results = (
        db.query(WorkoutResult)
        .filter(
            WorkoutResult.user_id == current_user.id,
            WorkoutResult.exercise_name == exercise_name,
        )
        .order_by(WorkoutResult.timestamp.desc())
        .all()
    )

    if not results:
        raise HTTPException(status_code=404, detail=f"No data for exercise: {exercise_name}")

    best_reps = max(r.repeated_count for r in results)
    total_reps = sum(r.repeated_count for r in results)
    avg_confidence = sum(r.confidence for r in results) / len(results)
    total_calories = sum(r.calorie for r in results)

    return ExerciseAnalytics(
        exercise_name=exercise_name,
        total_sessions=len(results),
        total_reps=total_reps,
        best_reps=best_reps,
        avg_confidence=round(avg_confidence, 4),
        total_calories=round(total_calories, 2),
        last_session=results[0] if results else None,
    )


@analytics_router.get("/streak")
def get_streak(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    return {"streak": calculate_streak(db, current_user.id)}


def calculate_streak(db: Session, user_id: int) -> int:
    results = (
        db.query(func.date(WorkoutResult.timestamp).label("date"))
        .filter(WorkoutResult.user_id == user_id)
        .distinct()
        .order_by(func.date(WorkoutResult.timestamp).desc())
        .all()
    )
    if not results:
        return 0

    streak = 0
    today = datetime.now(timezone.utc).date()
    expected = today

    for (r,) in results:
        d = r if isinstance(r, datetime) else datetime.fromisoformat(str(r)).date()
        if d == expected or d == expected - timedelta(days=1):
            streak += 1
            expected = d - timedelta(days=1)
        elif d < expected - timedelta(days=1):
            break
    return streak

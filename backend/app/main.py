from fastapi import FastAPI, APIRouter, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import tempfile
import os
import json

from app.database import engine, Base
from app.api.routes import (
    router as auth_router,
    plan_router,
    workout_router,
    summary_router,
    analytics_router,
)
from app.ml.trainer import (
    train_knn_model,
    predict_pose,
    analyze_form_landmarks,
    generate_training_csv_from_session,
)

Base.metadata.create_all(bind=engine)

app = FastAPI(
    title="RepDetect Backend API",
    description="Backend API for the RepDetect fitness tracking app with pose estimation and ML",
    version="2.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth_router)
app.include_router(plan_router)
app.include_router(workout_router)
app.include_router(summary_router)
app.include_router(analytics_router)


@app.get("/health")
def health_check():
    return {
        "status": "ok",
        "service": "repdetect-backend",
        "version": "2.0.0",
    }


ml_router = APIRouter(prefix="/api/ml", tags=["ml"])


@ml_router.post("/train")
def train_model(file_paths: list[str], auto_tune: bool = True):
    try:
        result = train_knn_model(file_paths, auto_tune=auto_tune)
        return result
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Training failed: {str(e)}")


@ml_router.post("/predict")
def predict(embedding: list[float], model_name: str = "pose_classifier.pkl"):
    try:
        return predict_pose(embedding, model_name)
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Prediction failed: {str(e)}")


@ml_router.post("/analyze-form")
def analyze_form(landmarks: list[dict]):
    try:
        return analyze_form_landmarks(landmarks)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@ml_router.post("/generate-csv")
async def generate_csv(
    file: UploadFile = File(...),
    class_name: str = "exercise",
):
    content = await file.read()
    data = json.loads(content)

    landmarks_history = data.get("landmarks_history", [])
    if not landmarks_history:
        raise HTTPException(status_code=400, detail="landmarks_history is required")

    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".csv")
    try:
        output = generate_training_csv_from_session(landmarks_history, class_name, tmp.name)
        with open(output, "r") as f:
            csv_content = f.read()
        return {"csv": csv_content, "rows": len(landmarks_history)}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))
    finally:
        os.unlink(tmp.name)


app.include_router(ml_router)

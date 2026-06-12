import pandas as pd
import numpy as np
from sklearn.neighbors import KNeighborsClassifier
from sklearn.neighbors import NearestNeighbors
from sklearn.model_selection import train_test_split, cross_val_score, GridSearchCV
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
import pickle
import os
import json
from typing import List, Tuple, Optional
from datetime import datetime, timedelta

MODEL_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "ml", "models")
os.makedirs(MODEL_DIR, exist_ok=True)


def load_training_data(csv_paths: List[str]) -> Tuple[pd.DataFrame, pd.Series]:
    frames = []
    for path in csv_paths:
        if not os.path.exists(path):
            continue
        df = pd.read_csv(path, header=None)
        if df.empty:
            continue
        X = df.iloc[:, :-1]
        y = df.iloc[:, -1].astype(str).str.strip()
        frames.append(pd.concat([X, y], axis=1))
    if not frames:
        raise ValueError("No valid CSV files found")
    combined = pd.concat(frames, ignore_index=True)
    combined = combined.dropna()
    X = combined.iloc[:, :-1].astype(np.float32)
    y = combined.iloc[:, -1]
    return X, y


def train_knn_model(
    csv_paths: List[str],
    model_name: str = "pose_classifier.pkl",
    auto_tune: bool = True,
    test_size: float = 0.2
) -> dict:
    X, y = load_training_data(csv_paths)

    unique_classes = y.nunique()
    n_neighbors = min(15, max(3, int(len(X) * 0.01)))
    n_neighbors = min(n_neighbors, unique_classes * 3)

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=test_size, random_state=42, stratify=y
    )

    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)

    if auto_tune and len(X_train) > 100:
        param_grid = {
            "n_neighbors": [max(3, n_neighbors - 5), n_neighbors, n_neighbors + 5],
            "weights": ["uniform", "distance"],
            "p": [1, 2],
        }
        grid = GridSearchCV(
            KNeighborsClassifier(),
            param_grid,
            cv=min(5, len(X_train) // 10),
            scoring="accuracy",
            n_jobs=-1,
        )
        grid.fit(X_train_scaled, y_train)
        model = grid.best_estimator_
        best_params = grid.best_params_
    else:
        model = KNeighborsClassifier(n_neighbors=n_neighbors, weights="distance", p=2)
        model.fit(X_train_scaled, y_train)
        best_params = {"n_neighbors": n_neighbors, "weights": "distance", "p": 2}

    y_pred = model.predict(X_test_scaled)
    report = classification_report(y_test, y_pred, output_dict=True, zero_division=0)
    cm = confusion_matrix(y_test, y_pred)

    cv_scores = cross_val_score(model, X_train_scaled, y_train, cv=min(5, len(X_train) // 10))

    model_pipeline = {"scaler": scaler, "model": model}
    model_path = os.path.join(MODEL_DIR, model_name)
    with open(model_path, "wb") as f:
        pickle.dump(model_pipeline, f)

    return {
        "model_name": model_name,
        "accuracy": round(report.get("accuracy", 0), 4),
        "cv_mean_accuracy": round(float(cv_scores.mean()), 4),
        "cv_std_accuracy": round(float(cv_scores.std()), 4),
        "best_params": best_params,
        "classification_report": {k: v for k, v in report.items() if isinstance(v, dict)},
        "confusion_matrix": cm.tolist(),
        "model_path": model_path,
        "train_samples": len(X_train),
        "test_samples": len(X_test),
        "num_classes": unique_classes,
    }


def load_model(model_name: str = "pose_classifier.pkl") -> dict:
    model_path = os.path.join(MODEL_DIR, model_name)
    if not os.path.exists(model_path):
        raise FileNotFoundError(f"Model {model_name} not found. Train it first.")
    with open(model_path, "rb") as f:
        return pickle.load(f)


def predict_pose(embedding: List[float], model_name: str = "pose_classifier.pkl") -> dict:
    pipeline = load_model(model_name)
    scaler = pipeline["scaler"]
    model = pipeline["model"]

    X = np.array(embedding, dtype=np.float32).reshape(1, -1)
    X_scaled = scaler.transform(X)

    pred = model.predict(X_scaled)[0]
    pred_probs = model.predict_proba(X_scaled)[0]

    top_indices = np.argsort(pred_probs)[::-1][:3]
    top_predictions = [
        {
            "class": str(model.classes_[i]),
            "confidence": round(float(pred_probs[i]), 4),
        }
        for i in top_indices
        if pred_probs[i] > 0.01
    ]

    neighbors = model.kneighbors(X_scaled, return_distance=True)
    neighbor_info = [
        {
            "class": str(model.classes_[model.predict(neighbor.reshape(1, -1))[0]]),
            "distance": round(float(dist), 4),
        }
        for dist, neighbor in zip(neighbors[0][0], neighbors[1][0])
    ]

    return {
        "predicted_class": str(pred),
        "confidence": round(float(max(pred_probs)), 4),
        "top_predictions": top_predictions,
        "nearest_neighbors": neighbor_info,
    }


def analyze_form_landmarks(landmarks_33: List[dict]) -> dict:
    if len(landmarks_33) < 33:
        return {"error": "Need all 33 landmarks"}

    angles = {}
    pairs = [
        ("left_elbow", 11, 13, 15),
        ("right_elbow", 12, 14, 16),
        ("left_knee", 23, 25, 27),
        ("right_knee", 24, 26, 28),
        ("left_hip_flex", 11, 23, 25),
        ("right_hip_flex", 12, 24, 26),
        ("left_shoulder", 5, 11, 13),
        ("right_shoulder", 6, 12, 14),
        ("torso_left", 11, 23, 25),
        ("torso_right", 12, 24, 26),
    ]

    for name, i, j, k in pairs:
        a = landmarks_33[i]
        b = landmarks_33[j]
        c = landmarks_33[k]
        angle = _angle_between(a, b, c)
        angles[name] = round(angle, 1)

    return {
        "joint_angles": angles,
        "posture_flags": {
            "left_knee_over_toe": angles.get("left_knee", 0) < 70,
            "right_knee_over_toe": angles.get("right_knee", 0) < 70,
            "rounded_back": angles.get("torso_left", 90) > 45,
        },
    }


def _angle_between(a: dict, b: dict, c: dict) -> float:
    v1 = np.array([a["x"] - b["x"], a["y"] - b["y"], a.get("z", 0) - b.get("z", 0)])
    v2 = np.array([c["x"] - b["x"], c["y"] - b["y"], c.get("z", 0) - b.get("z", 0)])
    dot = np.dot(v1, v2)
    mag = np.linalg.norm(v1) * np.linalg.norm(v2)
    if mag < 1e-6:
        return 0.0
    cos_angle = np.clip(dot / mag, -1.0, 1.0)
    return float(np.degrees(np.arccos(cos_angle)))


def generate_training_csv_from_session(
    landmarks_history: List[List[dict]],
    class_name: str,
    output_path: str,
) -> str:
    rows = []
    for landmarks in landmarks_history:
        if len(landmarks) < 33:
            continue
        embedding = []
        pairs_indices = [
            (23, 24, 11, 12),
            (11, 13), (12, 14),
            (13, 15), (14, 16),
            (23, 25), (24, 26),
            (25, 27), (26, 28),
            (11, 15), (12, 16),
            (23, 27), (24, 28),
            (11, 13, 15), (12, 14, 16),
            (23, 25, 27), (24, 26, 28),
        ]
        for pair in pairs_indices:
            if len(pair) == 2:
                i, j = pair
                a = landmarks[i]
                b = landmarks[j]
                embedding.extend([a["x"] - b["x"], a["y"] - b["y"], a.get("z", 0) - b.get("z", 0)])
            elif len(pair) == 3:
                i, j, k = pair
                a = landmarks[i]
                b = landmarks[j]
                c = landmarks[k]
                embedding.append(_angle_between(a, b, c))
        embedding.append(class_name)
        rows.append(embedding)

    if not rows:
        raise ValueError("No valid landmarks to generate CSV")

    df = pd.DataFrame(rows)
    df.to_csv(output_path, index=False, header=False)
    return output_path

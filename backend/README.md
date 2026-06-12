# RepDetect Backend

Backend Python/FastAPI pour l'application RepDetect.

## Installation

```bash
cd backend
pip install -r requirements.txt
```

## Configuration

Copier `.env.example` vers `.env` et modifier les valeurs.

## Lancer le serveur

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## API Endpoints

### Authentification
- `POST /api/auth/register` - Créer un compte
- `POST /api/auth/login` - Se connecter (reçoit un JWT)
- `GET /api/auth/me` - Profil utilisateur

### Plans
- `GET /api/plans/` - Liste des plans
- `POST /api/plans/` - Créer un plan
- `PUT /api/plans/{id}` - Modifier un plan
- `DELETE /api/plans/{id}` - Supprimer un plan

### Workouts
- `GET /api/workouts/` - Historique des entraînements
- `POST /api/workouts/` - Sauvegarder un résultat
- `DELETE /api/workouts/{id}` - Supprimer

### Résumé
- `GET /api/summary/` - Statistiques globales

### Machine Learning
- `POST /api/ml/train` - Entraîner le modèle K-NN
- `POST /api/ml/predict` - Prédire une pose

## Documentation API

Une fois le serveur lancé : http://localhost:8000/docs

# Diagrammes UML - RepDetect (Coach Sportif Mobile)

## 1. Diagramme de Cas d'Utilisation

```mermaid
graph TD
    A[Utilisateur] --> B[Créer un Plan d'Exercice]
    A --> C[Démarrer un Entraînement]
    A --> D[Visualiser les Résultats]
    A --> E[Consulter l'Historique]
    A --> F[Basculer la Caméra]
    
    C --> G[Compter les Répétitions]
    C --> H[Classifier la Pose]
    C --> I[Afficher le Feedback Temps Réel]
    C --> J[Annonce Vocale]
    
    B --> K[Sélectionner un Exercice]
    B --> L[Définir le Nombre de Répétitions]
    B --> M[Choisir les Jours]
    
    D --> N[Afficher Résumé Exercise]
    D --> O[Afficher Confiance Yoga]
    
    E --> P[Graphique Hebdomadaire]
    E --> Q[Activité Récente]
    
    subgraph Système
        R[Détection de Pose ML Kit]
        S[Classification K-NN]
        T[Compteur de Répétitions]
        U[Base de Données Room]
    end
    
    G --> T
    H --> S
    S --> R
    B --> U
    D --> U
    E --> U
```

## 2. Diagramme de Classes

```mermaid
classDiagram
    %% ===== Activities =====
    class SplashActivity {
        +onCreate()
    }
    class OnboardingActivity {
        -viewPager2: ViewPager2
        +onCreate()
    }
    class MainActivity {
        -binding: ActivityMainBinding
        -navController: NavController
        +workoutResultData: String
        +workoutTimer: String
        +onCreate()
        +onSupportNavigateUp(): Boolean
    }

    %% ===== Fragments =====
    class HomeFragment {
        -homeViewModel: HomeViewModel
        +onCreateView()
    }
    class WorkOutFragment {
        -cameraViewModel: CameraXViewModel
        -resultViewModel: ResultViewModel
        -addPlanViewModel: AddPlanViewModel
        -textToSpeech: TextToSpeech
        -notCompletedExercise: List~Plan~
        -exerciseLog: ExerciseLog
        +onCreateView()
        +clearMemory()
        -bindAllCameraUseCases()
        -bindAnalysisUseCase()
        -displayConfidence()
        -displayResult()
        -synthesizeSpeech()
    }
    class PlanStepOneFragment {
        -addPlanViewModel: AddPlanViewModel
        +onCreateView()
    }
    class PlanStepTwoFragment {
        -addPlanViewModel: AddPlanViewModel
        +onCreateView()
    }
    class ProfileFragment {
        -resultViewModel: ResultViewModel
        +onCreateView()
    }
    class CompletedFragment {
        +onCreateView()
    }
    class CancelFragment {
        +onCreateView()
    }
    class OnboardingFragment {
        <<abstract>>
    }

    %% ===== ViewModels =====
    class CameraXViewModel {
        -cameraProviderLiveData: MutableLiveData~ProcessCameraProvider~
        -postureLiveData: MutableLiveData~Map~String, PosturResult~~
        -triggerClassification: MutableLiveData~Boolean~
        +getProcessCameraProvider(): LiveData
        +getPostureLiveData(): MutableLiveData
        +getTriggerClassification(): MutableLiveData
    }
    class HomeViewModel {
        -repository: AppRepository
        +getNotCompletePlans(): List~Plan~
    }
    class AddPlanViewModel {
        -repository: AppRepository
        +insertPlan()
        +updateComplete()
        +deletePlan()
    }
    class ResultViewModel {
        -repository: AppRepository
        +insert()
        +getAllResult()
        +getRecentWorkout()
    }

    %% ===== Data Layer =====
    class AppDatabase {
        <<singleton>>
        +getDatabase(): AppDatabase
        +planDao(): PlanDataDao
        +resultDao(): WorkoutResultDao
    }
    class AppRepository {
        -planDao: PlanDataDao
        -resultDao: WorkoutResultDao
        +allPlans: LiveData~List~Plan~~
        +insertPlan()
        +updatePlan()
        +deletePlan()
        +insertResult()
        +getRecentWorkout()
    }
    class Plan {
        <<Room Entity>>
        +id: Int
        +exercise: String
        +calories: Double
        +repeatCount: Int
        +selectedDays: String
        +completed: Boolean
        +timeCompleted: Long?
    }
    class WorkoutResult {
        <<Room Entity>>
        +id: Int
        +exerciseName: String
        +repeatedCount: Int
        +confidence: Float
        +timestamp: Long
        +calorie: Double
        +workoutTimeInMin: Double
    }
    class PlanDataDao {
        <<interface / DAO>>
        +getAll(): LiveData~List~Plan~~
        +getPlansByDay(): List~Plan~
        +getNotCompletePlanByDay(): MutableList~Plan~
        +insert()
        +update()
        +addCompletedTime()
        +deletePlan()
    }
    class WorkoutResultDao {
        <<interface / DAO>>
        +getAll(): List~WorkoutResult~
        +getRecentWorkout(): List~WorkoutResult~
        +insert()
    }
    class PostureResult {
        +repetition: Int
        +confidence: Float
        +postureType: String
    }
    class ExerciseLog {
        +addExercise()
        +getExerciseData(): ExerciseData
        +getExerciseDataList(): List~ExerciseData~
        +areAllExercisesCompleted(): Boolean
    }
    class ExercisePlan {
        +planId: Int?
        +exerciseName: String
        +repetitions: Int
    }

    %% ===== Pose Detection =====
    class PoseDetectorProcessor {
        -detector: PoseDetector
        -poseClassifierProcessor: PoseClassifierProcessor
        -cameraXViewModel: CameraXViewModel
        +detectInImage(): Task~PoseWithClassification~
        +onSuccess()
        +onFailure()
        +stop()
    }
    class PoseClassifierProcessor {
        -poseClassifier: PoseClassifier
        -emaSmoothing: EMASmoothing
        -repCounters: List~RepetitionCounter~
        +getPoseResult(): Map~String, PostureResult~
    }
    class PoseClassifier {
        -poseSamples: List~PoseSample~
        -MAX_DISTANCE_TOP_K: int
        -MEAN_DISTANCE_TOP_K: int
        +classify(): ClassificationResult
        +confidenceRange(): int
    }
    class PoseEmbedding {
        +getPoseEmbedding(): List~PointF3D~
    }
    class PoseSample {
        +getClassName(): String
        +getEmbedding(): List~PointF3D~
        +getPoseSample(): PoseSample
    }
    class ClassificationResult {
        +incrementClassConfidence()
        +getClassConfidence(): float
        +getMaxConfidenceClass(): String
        +getAllClasses(): Set~String~
        +putClassConfidence()
    }
    class RepetitionCounter {
        -className: String
        -numRepeats: int
        -poseEntered: boolean
        -enterThreshold: float
        -exitThreshold: float
        +addClassificationResult(): int
        +getNumRepeats(): int
        +getClassName(): String
    }
    class EMASmoothing {
        -window: Deque~ClassificationResult~
        -alpha: float
        -windowSize: int
        +getSmoothedResult(): ClassificationResult
    }

    %% ===== Graphic =====
    class GraphicOverlay {
        +add()
        +clear()
        +setImageSourceInfo()
    }
    class PoseGraphic {
        -pose: Pose
        +draw()
    }

    %% ===== Relationships =====

    %% Activities -> Fragments
    MainActivity --> HomeFragment : navigue vers
    MainActivity --> WorkOutFragment : navigue vers
    MainActivity --> PlanStepOneFragment : navigue vers
    MainActivity --> ProfileFragment : navigue vers

    %% Fragments -> ViewModels
    WorkOutFragment --> CameraXViewModel : observe
    WorkOutFragment --> ResultViewModel : utilise
    WorkOutFragment --> AddPlanViewModel : utilise
    WorkOutFragment --> HomeViewModel : utilise
    HomeFragment --> HomeViewModel : observe
    ProfileFragment --> ResultViewModel : observe
    PlanStepOneFragment --> AddPlanViewModel : utilise
    PlanStepTwoFragment --> AddPlanViewModel : utilise

    %% ViewModels -> Repository
    HomeViewModel --> AppRepository
    AddPlanViewModel --> AppRepository
    ResultViewModel --> AppRepository

    %% Repository -> Database
    AppRepository --> AppDatabase
    AppDatabase --> PlanDataDao
    AppDatabase --> WorkoutResultDao
    PlanDataDao --> Plan
    WorkoutResultDao --> WorkoutResult

    %% Pose Detection Pipeline
    PoseDetectorProcessor --> PoseClassifierProcessor : utilise
    PoseDetectorProcessor --> CameraXViewModel : poste résultats
    PoseDetectorProcessor --> PoseGraphic : dessine squelette
    PoseClassifierProcessor --> PoseClassifier : classifie
    PoseClassifierProcessor --> EMASmoothing : lisse
    PoseClassifierProcessor --> RepetitionCounter : compte reps
    PoseClassifier --> PoseEmbedding : calcule embedding
    PoseClassifier --> PoseSample : charge données CSV
    PoseGraphic --> GraphicOverlay : s'affiche sur

    %% WorkOutFragment details
    WorkOutFragment --> ExerciseLog : gère
    WorkOutFragment --> ExercisePlan : planifie
    WorkOutFragment --> PostureResult : affiche
```

## 3. Diagramme de Séquence - Entraînement Complet

```mermaid
sequenceDiagram
    participant U as Utilisateur
    participant WF as WorkOutFragment
    participant VM as CameraXViewModel
    participant AI as ImageAnalysis
    participant PDP as PoseDetectorProcessor
    participant PCP as PoseClassifierProcessor
    participant PC as PoseClassifier
    participant RC as RepetitionCounter
    participant EMS as EMASmoothing
    participant DB as Room Database
    participant UI as UI Views
    
    U->>WF: Ouvre l'onglet Workout
    WF->>VM: getProcessCameraProvider()
    VM-->>WF: ProcessCameraProvider
    WF->>WF: bindPreviewUseCase()
    WF->>VM: observe(triggerClassification)
    WF->>WF: charge plans non complétés
    
    U->>WF: Clique "Start Exercise"
    WF->>VM: triggerClassification = true
    WF->>WF: bindAnalysisUseCase()
    activate AI
    AI->>AI: setAnalyzer(callback)
    
    loop Chaque frame caméra
        AI->>PDP: detectInImage(InputImage)
        activate PDP
        PDP->>PDP: PoseDetection.getClient().process(image)
        PDP->>PCP: getPoseResult(pose)
        activate PCP
        
        PCP->>PC: classify(pose)
        activate PC
        PC->>PC: extractPoseLandmarks()
        PC->>PoseEmbedding: getPoseEmbedding()
        PC->>PC: K-NN avec outlier filtering
        PC-->>PCP: ClassificationResult
        deactivate PC
        
        PCP->>EMS: getSmoothedResult(classification)
        activate EMS
        EMS-->>PCP: ClassificationResult lissé
        deactivate EMS
        
        loop Pour chaque RepetitionCounter
            PCP->>RC: addClassificationResult(smoothed)
            activate RC
            RC-->>PCP: numRepeats
            deactivate RC
        end
        
        PCP-->>PDP: Map<String, PostureResult>
        deactivate PCP
        
        PDP->>VM: postureLiveData.postValue(results)
        PDP-->>AI: PoseWithClassification
        deactivate PDP
        
        VM-->>WF: onChanged(mapResult)
        WF->>WF: displayResult() / displayConfidence()
        WF->>UI: met à jour TextViews / RecyclerView
        
        alt Exercise rep counting
            WF->>WF: exerciseLog.addExercise()
            WF->>DB: addPlanViewModel.updateComplete()
        else Yoga pose
            WF->>WF: confIndicatorView.setBackground()
        end
    end
    
    U->>WF: Clique "Complete Exercise"
    WF->>DB: insert(WorkoutResult)
    WF->>VM: triggerClassification = false
    WF-->>U: Navigue vers CompletedFragment
    
    deactivate AI
```

## 4. Diagramme d'Activité - Cycle d'Entraînement

```mermaid
graph TD
    A([Démarrage App]) --> B{First Launch?}
    B -->|Oui| C[Afficher Onboarding]
    B -->|Non| D[Écran d'Accueil]
    C --> D
    
    D --> E[Créer Plan d'Exercice]
    D --> F[Aller à Workout]
    D --> G[Voir Profil]
    
    E --> H[Sélectionner Exercice<br/>+ Difficulté]
    H --> I[Définir Répétitions<br/>+ Jours]
    I --> J[Sauvegarder Plan]
    J --> D
    
    F --> K[Charger Plans du Jour]
    K --> L[Afficher GIFs Démo]
    L --> M{Prêt?}
    M -->|Oui| N[Démarrer Workout]
    M -->|Non| L
    
    N --> O[Activer Caméra]
    O --> P[Activer ML Kit Pose]
    
    P --> Q{Loop: Chaque Frame}
    
    Q --> R[Détecter Pose]
    R --> S[Générer Embedding<br/>26 landmarks]
    S --> T[Classifier K-NN<br/>2-stage outlier filtering]
    T --> U[EMA Smoothing<br/>10 frames window]
    U --> V[État: compteur reps]
    
    V --> W{Confiance > 6?}
    W -->|Non| Q
    W -->|Oui| X[Marquer pose entrée]
    X --> Y{Confiance < 4?}
    Y -->|Non| Q
    Y -->|Oui| Z[Incrémenter répétition<br/>+ Bip sonore]
    
    Z --> AA[Répétitions >= Cible?]
    AA -->|Non| Q
    AA -->|Oui| AB[TTS: Exercise Complete]
    AB --> AC[Tout terminé?]
    AC -->|Non| Q
    AC -->|Oui| AD[TTS: Congratulations!]
    
    Q --> AE{User stop?}
    AE -->|Complete| AF[Sauvegarder Résultat<br/>+ Calories + Temps]
    AE -->|Cancel| AG[Annuler session]
    AF --> AH[Afficher Résumé]
    AH --> D
    AG --> D
    
    G --> AI[Graphique Calorifique<br/>Hebdomadaire]
    AI --> D
    
    style A fill:#4CAF50,color:white
    style N fill:#2196F3,color:white
    style P fill:#FF9800,color:white
    style AE fill:#F44336,color:white
```

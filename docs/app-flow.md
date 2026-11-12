# Class Attendance — App Flow Diagram

```mermaid
flowchart TD
    S["🚀 splash<br>(SplashActivity)"]
    L["🔑 login<br>(LoginActivity)"]
    R["📝 register<br>(RegisterActivity)"]
    D["🏠 dashboard<br>(DashboardActivity)"]
    C["📚 classes<br>(ClassListActivity)"]
    AC["➕ add_class<br>(AddClassActivity)"]
    STU["👥 students<br>(ManageStudentsActivity)"]
    ADDS["👤 add_student<br>(AddStudentActivity)"]
    A["✅ attendance<br>(TakeAttendanceActivity)"]
    RP["📊 reports<br>(ReportsActivity)"]

    %% Entry
    APP(["App launched"]) --> S
    S -- "after 2s delay ✅" --> L
    S -. "skip straight in<br>(not coded)" .-> D

    %% Auth
    L -- "Sign In not coded" -.-> D
    L -. "No account? Register" .-> R
    R -. "Already have account" .-> L
    R -. "Sign up not coded" .-> D

    %% Dashboard hub
    D -- "Manage Classes ✅" --> C
    D -- "Manage Students" -.-> STU
    D -- "Take Attendance" -.-> A
    D -- "Reports" -.-> RP

    %% Classes
    C -- "FAB + ✅" --> AC
    AC -- "Save class ✅" --> C
    AC -- "Back ✅" --> C

    %% Students
    STU -. "Add Student" .-> ADDS
    STU -. "Edit / Delete row" .-> STU

    %% Attendance
    A -. "Mark P / A / L" .-> A
    A -. "Bottom bar → done" .-> D
```

# Delta for Backend API

The whole `backend-api` capability is removed: the Spring Boot backend under `backend/` is deleted and replaced by Firebase (Auth and Firestore) under `firebase-cloud-sync`.

## REMOVED Requirements

### Requirement: Separate Backend Build
(Reason: replaced by Firebase, firebase-cloud-sync. The `backend/` Gradle build is deleted.)

### Requirement: Hexagonal Domain and Use Cases
(Reason: replaced by Firebase, firebase-cloud-sync. The backend domain no longer exists; the app keeps its own hexagonal ports.)

### Requirement: Owner-Scoped Persistence
(Reason: replaced by Firebase, firebase-cloud-sync. Per-user isolation is now enforced by `firestore-security-rules`.)

### Requirement: Integration Tests Against PostgreSQL
(Reason: replaced by Firebase, firebase-cloud-sync. PostgreSQL is no longer used; rules are tested against the Firebase emulator.)

### Requirement: Deny-by-Default Security
(Reason: replaced by Firebase, firebase-cloud-sync. Default deny is now owned by `firestore-security-rules` and `user-auth`.)

### Requirement: Actuator Exposes Only Health
(Reason: replaced by Firebase, firebase-cloud-sync. No server application remains.)

### Requirement: OpenAPI Contract as Source of Truth
(Reason: replaced by Firebase, firebase-cloud-sync. No HTTP API remains.)

### Requirement: Request Validation and Safe Errors
(Reason: replaced by Firebase, firebase-cloud-sync. Validation is now owned by `firestore-security-rules`.)

### Requirement: Contract Tests
(Reason: replaced by Firebase, firebase-cloud-sync. No HTTP contract remains.)

### Requirement: Mutation Testing Gate
(Reason: replaced by Firebase, firebase-cloud-sync. The backend PIT gate is deleted with the backend.)

### Requirement: Hardened Container Image
(Reason: replaced by Firebase, firebase-cloud-sync. No container image is built or published.)

### Requirement: Database Credentials From Environment Only
(Reason: replaced by Firebase, firebase-cloud-sync. No database credentials exist.)

### Requirement: Packaged Artifact Free of Credentials
(Reason: replaced by Firebase, firebase-cloud-sync. No backend artifact is packaged.)

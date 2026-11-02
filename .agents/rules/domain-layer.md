# Domain Layer Guardrails (`modular-clean`)

1. **Zero Framework Leakage**: Domain files MUST NOT import `android.content.`, `android.view.`, `androidx.compose.`, `androidx.room.`, `retrofit2.`, `okhttp3.`, `io.ktor.`.
2. **Ownership**: Domain owns business Entities, Value Objects, narrow Repository interfaces (ISP), UseCases/Policies, and `DomainError`.
3. **Pure Unit Tests**: Every Domain class must be testable in milliseconds using pure in-memory Fakes.


https://www.kaggle.com/competitions/rossmann-store-sales/overview
┌─────────────────────────────────────────────────────┐
│                   Frontend (Next.js/React)           │
│              vananhnt994/ovacp_frontend              │
└──────────────────────┬──────────────────────────────┘
                       │ REST/HTTP
                       ▼
┌─────────────────────────────────────────────────────┐
│              API Gateway (Spring Cloud Gateway)      │
│                    Port: 8080                        │
└────┬──────────────┬───────────────┬─────────────────┘
     │              │               │
     ▼              ▼               ▼
┌─────────┐  ┌──────────┐  ┌───────────────┐
│  File   │  │   AI /   │  │ Visualization │
│ Service │  │ Analysis │  │   Service     │
│  :8081  │  │ Service  │  │    :8083      │
│         │  │  :8082   │  │               │
└────┬────┘  └────┬─────┘  └───────┬───────┘
     │            │                │
     ▼            ▼                ▼
  MinIO/S3    OpenAI/           PostgreSQL/
  Storage     Gemini API        Redis Cache

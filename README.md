# BxoStock: Smart Inventory & Order Management System

A supermarket inventory and order system with a **web app**, an **Android app**, one **REST API** and one **PostgreSQL** database. Built for the Codebxo Full Stack Developer internship assessment.

## Live links

| What | Link |
|---|---|
| Web app | https://bxostock.vercel.app |
| Backend API | https://smart-inventory-api-4k6o.onrender.com |
| API docs (Swagger) | https://smart-inventory-api-4k6o.onrender.com/swagger-ui.html |
| Android APK | https://github.com/TH-FARHAN/smart-inventory-system/releases/download/v1.0/BxoStock.apk |
| Demo video | https://drive.google.com/file/d/1qsCp3rTLKA6KDpXA3ZqsKbxcaNlsmREN/view?usp=sharing |
| Source code | https://github.com/TH-FARHAN/smart-inventory-system |
| ER diagram | [docs/ER_DIAGRAM.png](docs/ER_DIAGRAM.png) (source: [docs/er-diagram.mmd](docs/er-diagram.mmd)) |

> **Note:** the backend runs on a free Render plan and sleeps when idle. **The first request can take 50 seconds or more.** Please wait for it; later requests are fast.

## Demo logins

| Role | Email | Password |
|---|---|---|
| Admin | admin@inventory.demo | provided in the submission email |
| Staff | jestin@gmail.com | provided in the submission email |

On the login screen, choose the **Admin** or **Staff** tab that matches the account.

## Modules (all required modules are implemented)

1. **Authentication:** login, logout, role-based access (ADMIN and STAFF), math captcha, change password, forgot password by email code (admins).
2. **Dashboard:** products, stock, orders and sales summary, low-stock list, recent orders. Admins also get sales charts (weekly, monthly, yearly, custom range) and top products.
3. **Products:** add, edit, delete (soft delete) and live search. Admin only for changes.
4. **Inventory:** stock in, stock out, low-stock alerts, movement history.
5. **Customers:** add, edit and search.
6. **Orders:** create orders with automatic stock deduction, cancel with stock restored, undo, bill PDF.
7. **Reports:** sales and inventory reports with PDF download (admin).

**Extras:** returns and refunds, suppliers and purchase orders (receiving stock), staff performance report, audit log, profile photos, user management with password reset.

## Roles

| | ADMIN | STAFF |
|---|---|---|
| Dashboard, Orders, Returns, Customers, Stock in/out, view Products | Yes | Yes |
| Add, edit, delete Products and Categories | Yes | No |
| Suppliers, Purchase orders | Yes | No |
| Users, Reports, Audit logs, Staff performance, sales charts | Yes | No |

There is **no public sign-up**. Only an admin creates accounts.

## Security

- Passwords are hashed with **BCrypt**; nobody, including admins, can read them.
- **JWT** tokens (1 hour). The backend reloads the user from the database on every request, so deactivating a user blocks their token immediately.
- Role checks are enforced in the **backend** (`@PreAuthorize`). The web and app only hide screens for convenience.
- Login protection: math captcha after 3 failures, account lock for 15 minutes after 5 failures, IP rate limit of 10 per minute, and a generic "Invalid email or password" message.
- Prices are always taken from the database, never from the client.
- Stock changes use **row locks** and a single transaction, so two cashiers cannot oversell.
- Every important action is written to the **audit log**.
- Secrets are environment variables and are not committed.

## Tech stack

- **Backend:** Java 17, Spring Boot, Spring Security + JWT, Spring Data JPA, Jakarta Validation, Swagger (springdoc)
- **Database:** PostgreSQL (Neon)
- **Web:** React (Vite), Bootstrap, Axios, React Router, Recharts, jsPDF
- **Android:** Java, XML layouts, Retrofit
- **Hosting:** Render (backend, Docker), Vercel (web)

## Project structure

```
backend/   Spring Boot REST API
web/       React web app
android/   Android app (Android Studio project)
docs/      ER diagram
postman/   Postman collection
```

## Run it locally

### Backend

Requirements: Java 17 and a PostgreSQL database.

Set these environment variables (or use an `application-local.properties`, which is git-ignored):

| Variable | Meaning |
|---|---|
| `DB_URL` | JDBC URL, e.g. `jdbc:postgresql://host/dbname?sslmode=require` |
| `DB_USER`, `DB_PASS` | database login |
| `JWT_SECRET` | long random secret |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | first admin, created on startup (password at least 8 characters) |
| `CORS_ORIGINS` | allowed web origins (default `http://localhost:5173`) |
| `BREVO_API_KEY`, `BREVO_SENDER_EMAIL`, `BREVO_SENDER_NAME` | optional, for the forgot-password email |

```
cd backend
./mvnw spring-boot:run
```

Tables are created automatically on first start. Swagger: http://localhost:8080/swagger-ui.html

### Web

```
cd web
npm install
```

Create `web/.env`:

```
VITE_API_URL=http://localhost:8080/api
```

Then start it:

```
npm run dev
```


Open http://localhost:5173

### Android

1. Open the `android` folder in Android Studio and let Gradle sync.
2. The API address is `BASE_URL` in `app/src/main/java/com/bxostock/app/api/ApiClient.java` (it points to the live backend).
3. Run on a phone or emulator, or use **Build > Generate App Bundles or APKs > Generate APKs**.

To install the APK, copy it to an Android phone, allow "Install unknown apps", and open it.

## Database

13 tables: users, categories, products, customers, orders, order_items, stock_movements, sales_returns, return_items, suppliers, purchase_orders, purchase_items, audit_logs. See [docs/ER_DIAGRAM.png](docs/ER_DIAGRAM.png).

## API documentation

Interactive Swagger docs: https://smart-inventory-api-4k6o.onrender.com/swagger-ui.html. A Postman collection is in the `postman` folder.

## Known limitations

- Sales totals, charts and reports do not yet subtract refunds from returns (returns are recorded and listed separately).
- No payment handling, discounts or GST; one shop location only.
- Dashboard charts and the staff report are calculated in the app from the orders list.
- Forgot password by email works for admin accounts only; staff ask an admin to reset.
- The free hosting plan sleeps when idle, so the first request is slow.

## Future enhancements

- **Offline and network handling:** detect Wi-Fi, mobile data or VPN changes, queue orders and stock changes while offline, and sync automatically when the connection returns (the Android app already shows a connection status).
- **Barcode scanning** with the phone camera (the SKU doubles as the barcode) to add products to an order quickly.
- **Expiry dates** with "expiring soon" alerts, important for a supermarket.
- **Net sales after returns** in the dashboard, charts and reports.
- **Billing upgrades:** discounts, GST, payment method (cash, UPI, card) and receipt printing.
- **Smart reorder suggestions** based on sales speed, and push notifications for low stock.
- **Multiple branches or counters** with per-branch stock.
- **Security upgrades:** refresh tokens, two-factor login, email verification for all roles.
- **Quality:** automated unit and integration tests, and a CI pipeline (GitHub Actions) that builds and tests on every push.
- **Server-side reports and pagination** for large data, and CSV/Excel export.
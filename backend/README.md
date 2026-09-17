# Crochet Store — Backend

Spring Boot 3 / Java 17 backend for the crochet e-commerce site.

## Import into Eclipse
1. Eclipse → File → Import → Maven → Existing Maven Projects
2. Point it at this `backend` folder → Finish
3. Eclipse will pull dependencies from Maven Central automatically

## Before running locally
1. Install PostgreSQL locally (or point `DB_URL` at a free Neon/Supabase instance)
2. Create a database, e.g. `crochet_store`
3. Set environment variables (or just edit the defaults in `application.properties` for local dev):
   - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
   - `JWT_SECRET` — any long random string
   - `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` (from cloudinary.com free account)
   - `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` (from Razorpay dashboard, test mode keys first)

## What's built so far
- `Product`, `AppUser`, `Order`, `OrderItem` entities
- `ProductRepository`, `AppUserRepository`, `OrderRepository`
- `ProductController` — public GET endpoints + admin create/update/soft-delete
- `AuthController` — `POST /api/auth/register`, `POST /api/auth/login` (returns a JWT)
- `JwtService` — issues and validates tokens
- `JwtAuthFilter` — reads the `Authorization: Bearer <token>` header on every request
  and sets the Spring Security context
- `SecurityConfig` — routes mapped (public vs `ADMIN`-only), JWT filter wired in and active

## Creating the first admin account
`/api/auth/register` always creates a `CUSTOMER` — on purpose, so nobody can self-promote
to admin over the API. To make your friend an admin:
1. Have her register normally through the site (or call `/api/auth/register` directly)
2. Manually update her row in the database: `UPDATE app_users SET role = 'ADMIN' WHERE email = '...';`

## How auth works end to end
1. `POST /api/auth/register` or `/login` → returns `{ token, email, fullName, role }`
2. Frontend stores the token and sends it on every request as `Authorization: Bearer <token>`
3. `JwtAuthFilter` validates it and marks the request as authenticated with the user's role
4. Routes under `/api/products/admin/**` and `/api/orders/admin/**` require the `ADMIN` role;
   everything else under `anyRequest().authenticated()` just requires a valid logged-in user

## Not yet built (next steps)
- `CartController` / cart logic
- `OrderController` + Razorpay order creation & payment signature verification
- Cloudinary upload endpoint for the admin panel
- Global exception handler (`@ControllerAdvice`) for clean error JSON

## Run locally
```
mvn spring-boot:run
```
(or just hit Run in Eclipse once imported)

Server starts on port 8080 by default.

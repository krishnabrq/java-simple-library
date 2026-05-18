# Library API Contract

This document outlines the REST APIs for the Library application, including their endpoints, descriptions, request payloads, and expected responses.

## Authentication APIs (`/api/v1/auth`)

### 1. Signup

Register a new member. By default, self-registered users are assigned the `MEMBER` role.

- **Method**: `POST`
- **Path**: `/api/v1/auth/signup`
- **Request Body**:

```json
{
  "signup": {
    "name": "Jane Doe",
    "email": "jane@example.com",
    "password": "password123"
  }
}
```

- **Response** `201 Created`:

```json
{
  "tokens": {
    "access_token": "eyJ...",
    "refresh_token": "def...",
    "token_type": "Bearer",
    "expires_in": 3600
  }
}
```

### 2. Login

Authenticate a user and return access/refresh tokens.

- **Method**: `POST`
- **Path**: `/api/v1/auth/login`
- **Request Body**:

```json
{
  "login": {
    "email": "jane@example.com",
    "password": "password123"
  }
}
```

- **Response** `200 OK`:

```json
{
  "tokens": {
    "access_token": "eyJ...",
    "refresh_token": "def...",
    "token_type": "Bearer",
    "expires_in": 3600
  }
}
```

### 3. Refresh Token

Obtain a new access token using a valid refresh token.

- **Method**: `POST`
- **Path**: `/api/v1/auth/refresh`
- **Request Body**:

```json
{
  "refresh": {
    "refresh_token": "def..."
  }
}
```

- **Response** `200 OK`:

```json
{
  "tokens": {
    "access_token": "eyJ...",
    "refresh_token": "def...",
    "token_type": "Bearer",
    "expires_in": 3600
  }
}
```

### 4. Logout

Invalidate the current user's session.

- **Method**: `POST`
- **Path**: `/api/v1/auth/logout`
- **Response** `204 No Content`

---

## Books APIs (`/api/v1/books`)

### 1. List Books

Retrieve a paginated list of books.

- **Method**: `GET`
- **Path**: `/api/v1/books`
- **Query Parameters**:
  - `page` (optional, default: 1): The page number to retrieve.
  - `limit` (optional, default: 10): Items per page (max 50).
- **Response** `200 OK`:

```json
{
  "books": [
    {
      "id": 1,
      "isbn": "9781234567890",
      "title": "Spring Boot in Action",
      "count": 5,
      "available_count": 4,
      "created_at": "2023-01-01T12:00:00Z",
      "updated_at": "2023-01-01T12:00:00Z"
    }
  ],
  "meta": {
    "total": 1,
    "next_page": null,
    "prev_page": null
  }
}
```

### 2. Get Book Details

Retrieve a single book by its ID.

- **Method**: `GET`
- **Path**: `/api/v1/books/{bookId}`
- **Response** `200 OK`:

```json
{
  "book": {
    "id": 1,
    "isbn": "9781234567890",
    "title": "Spring Boot in Action",
    "count": 5,
    "available_count": 4,
    "created_at": "2023-01-01T12:00:00Z",
    "updated_at": "2023-01-01T12:00:00Z"
  }
}
```

### 3. Create Book

Create a new book. Requires `STAFF` role.

- **Method**: `POST`
- **Path**: `/api/v1/books`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:

```json
{
  "book": {
    "isbn": "9781234567890",
    "title": "Spring Boot in Action",
    "count": 5
  }
}
```

- **Response** `201 Created`:

```json
{
  "book": {
    "id": 1,
    "isbn": "9781234567890",
    "title": "Spring Boot in Action",
    "count": 5,
    "available_count": 5,
    "created_at": "2023-01-01T12:00:00Z",
    "updated_at": "2023-01-01T12:00:00Z"
  }
}
```

### 4. Replace Book

Fully replace a book's mutable fields. Requires `STAFF` role.

- **Method**: `PUT`
- **Path**: `/api/v1/books/{bookId}`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:

```json
{
  "book": {
    "title": "Spring Boot in Action, 2nd Edition",
    "count": 10
  }
}
```

- **Response** `200 OK`:

```json
{
  "book": {
    "id": 1,
    "isbn": "9781234567890",
    "title": "Spring Boot in Action, 2nd Edition",
    "count": 10,
    "available_count": 9,
    "created_at": "2023-01-01T12:00:00Z",
    "updated_at": "2023-01-02T15:30:00Z"
  }
}
```

### 5. Update Book (Partial)

Partially update a book's fields. Requires `STAFF` role.

- **Method**: `PATCH`
- **Path**: `/api/v1/books/{bookId}`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:

```json
{
  "book": {
    "count": 15
  }
}
```

- **Response** `200 OK`:

```json
{
  "book": {
    "id": 1,
    "isbn": "9781234567890",
    "title": "Spring Boot in Action, 2nd Edition",
    "count": 15,
    "available_count": 14,
    "created_at": "2023-01-01T12:00:00Z",
    "updated_at": "2023-01-03T09:15:00Z"
  }
}
```

### 6. Delete Book

Delete a book. Requires `STAFF` role.

- **Method**: `DELETE`
- **Path**: `/api/v1/books/{bookId}`
- **Headers**: `Authorization: Bearer <token>`
- **Response** `204 No Content`

---

## Loans APIs (`/api/v1/loans`)

### 1. List Loans

Retrieve a paginated list of loans for the currently authenticated member. Requires `MEMBER` role.

- **Method**: `GET`
- **Path**: `/api/v1/loans`
- **Headers**: `Authorization: Bearer <token>`
- **Query Parameters**:
  - `page` (optional, default: 1): The page number to retrieve.
  - `limit` (optional, default: 10): Items per page (max 50).
- **Response** `200 OK`:

```json
{
  "loans": [
    {
      "id": 100,
      "book": {
        "id": 1,
        "name": "Spring Boot in Action"
      },
      "borrowed_at": "2023-01-01T12:00:00Z",
      "returned_at": null
    }
  ],
  "meta": {
    "total": 1,
    "next_page": null,
    "prev_page": null
  }
}
```

### 2. Borrow Book

Borrow a book. Requires `MEMBER` role.

- **Method**: `POST`
- **Path**: `/api/v1/loans`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:

```json
{
  "loan": {
    "book_id": 1
  }
}
```

- **Response** `201 Created`:

```json
{
  "loan": {
    "id": 100,
    "book_id": 1,
    "user_id": 42,
    "borrowed_at": "2023-01-01T12:00:00Z",
    "returned_at": null
  }
}
```

### 2. Return Book

Return a currently borrowed book. Requires `MEMBER` role.

- **Method**: `PATCH`
- **Path**: `/api/v1/loans/{loanId}/return`
- **Headers**: `Authorization: Bearer <token>`
- **Response** `200 OK`:

```json
{
  "loan": {
    "id": 100,
    "book_id": 1,
    "user_id": 42,
    "borrowed_at": "2023-01-01T12:00:00Z",
    "returned_at": "2023-01-05T12:00:00Z"
  }
}
```

---

## Notifications

The application emits internal events on certain actions and dispatches outbound notifications to third-party services. These are side-effects, not part of the REST contract, but clients should be aware of them.

### Welcome Notification (on Signup)

When a new member successfully registers via `POST /api/v1/auth/signup`, the application publishes an internal `UserSignedUpEvent` and asynchronously calls an external notifier service to send a welcome message. The signup response is **not** blocked by this call.

- **Trigger**: Successful signup (after the user is persisted)
- **Internal event**: `UserSignedUpEvent { userId, name, email }` (Spring `ApplicationEvent`)
- **Listener**: `WelcomeNotificationListener#onUserSignedUp` (runs on `@Async` thread)
- **Outbound call**:
  - **Method**: `POST`
  - **Path**: `{app.welcome-notifier.base-url}/posts`
  - **Default base URL**: `https://jsonplaceholder.typicode.com` (override with the `WELCOME_NOTIFIER_URL` env var or the `app.welcome-notifier.base-url` property)
  - **Request Body**:

  ```json
  {
    "userId": 42,
    "title": "Welcome, Jane Doe",
    "body": "Thanks for joining the library!"
  }
  ```

#### Resilience

The outbound call is wrapped with Resilience4j retry and circuit breaker, both registered under the instance name `welcomeNotifier`:

- **Retry**: up to 3 attempts, 500 ms initial wait, exponential backoff (multiplier 2). Only retries transient remote errors (`feign.RetryableException`, `feign.FeignException$FeignServerException`); 4xx responses are not retried.
- **Circuit breaker**: count-based sliding window of 10 calls, minimum 5 calls before evaluation, opens at a 50% failure rate, stays open for 30 s, then allows 3 probe calls in `HALF_OPEN`.
- **HTTP timeouts**: 2000 ms connect, 3000 ms read.
- **Fallback**: when retries are exhausted or the breaker is open, `WelcomeNotificationListener#onFailure` logs a warning. The signup itself is unaffected.

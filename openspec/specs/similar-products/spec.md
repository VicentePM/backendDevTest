# similar-products Specification

## Purpose

Expose a unified endpoint to aggregate and serve similar product details for a given product by composing upstream endpoints.

## Requirements

### Requirement: Retrieve Similar Products

The system MUST fetch similar product IDs and subsequently fetch their product details concurrently, returning a successful combined response. The response MUST preserve the order of the product IDs returned by the upstream endpoint.

#### Scenario: Normal flow with multiple similar products

- GIVEN a valid `productId`
- AND the upstream `similarids` endpoint returns 3 similar product IDs
- AND the upstream product detail endpoint returns valid details for all 3 IDs
- WHEN a `GET` request is made to `/product/{productId}/similar` on port 5000
- THEN the system returns a `200 OK` response
- AND the response body contains a JSON array of the 3 product details in the original ID order

### Requirement: Handle Missing Products

The system MUST return a `404 Not Found` if the requested base product cannot be found.

#### Scenario: Product not found

- GIVEN a `productId` that does not exist
- AND the upstream `similarids` endpoint returns a `404 Not Found` for that `productId`
- WHEN a `GET` request is made to `/product/{productId}/similar` on port 5000
- THEN the system returns a `404 Not Found` response

### Requirement: Handle Upstream Failures and Timeouts

The system MUST enforce a 3-second timeout per product detail call and handle partial failures gracefully. Any product detail call that times out or returns a `404 Not Found` or `500 Internal Server Error` MUST be silently skipped, returning only successfully fetched products.

#### Scenario: Slow upstream (Timeout)

- GIVEN a valid `productId` with multiple similar product IDs
- AND one of the upstream product detail calls takes longer than 3 seconds
- WHEN a `GET` request is made to `/product/{productId}/similar`
- THEN the system returns a `200 OK` response
- AND the slow product is excluded from the returned array

#### Scenario: Partial 404

- GIVEN a valid `productId` with multiple similar product IDs
- AND one of the upstream product detail calls returns a `404 Not Found`
- WHEN a `GET` request is made to `/product/{productId}/similar`
- THEN the system returns a `200 OK` response
- AND the missing product is excluded from the returned array

#### Scenario: Partial 500

- GIVEN a valid `productId` with multiple similar product IDs
- AND one of the upstream product detail calls returns a `500 Internal Server Error`
- WHEN a `GET` request is made to `/product/{productId}/similar`
- THEN the system returns a `200 OK` response
- AND the failing product is excluded from the returned array

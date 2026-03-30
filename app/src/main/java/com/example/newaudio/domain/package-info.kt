/**
 * Domain layer — business logic, use cases, and repository interfaces.
 *
 * This layer contains:
 * - Use cases that encapsulate business logic
 * - Domain models and data structures
 * - Repository interfaces that define data contracts
 * - Core business rules and validations
 *
 * Import rules:
 * - Must never import from feature/ or data/ packages
 * - Can import from util/ and domain/ packages only
 * - Should be framework-agnostic (no Android-specific dependencies)
 */
package com.example.newaudio.domain
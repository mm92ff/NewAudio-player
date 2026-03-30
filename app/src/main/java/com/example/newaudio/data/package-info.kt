/**
 * Data layer — repository implementations and data sources.
 *
 * This layer contains:
 * - Repository implementations that fulfill domain contracts
 * - Local data sources (Room database, DataStore, file operations)
 * - Remote data sources (network APIs, if applicable)
 * - Data mapping between external formats and domain models
 *
 * Import rules:
 * - Can import from domain/ and util/ packages
 * - Must never import from feature/ package
 * - Contains framework-specific implementations (Android, Room, etc.)
 */
package com.example.newaudio.data
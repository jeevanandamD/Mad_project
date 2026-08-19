package com.example.powerfix.data

// This file is deprecated and scheduled for removal after Firebase migration.
// Logic has been moved to AuthRepository.kt
interface TnebIdVerifier {
    suspend fun verify(role: AuthRole, tnebId: String): VerifyResult
}

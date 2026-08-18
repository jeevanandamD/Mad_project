package com.example.powerfix

import com.example.powerfix.data.AuthError
import com.example.powerfix.data.AuthRole
import com.example.powerfix.data.Complaint
import com.example.powerfix.data.EmergencyRequest
import com.example.powerfix.data.TnebIdValidator
import com.example.powerfix.data.UserProfile
import com.example.powerfix.data.userMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerFixModelsTest {

    @Test
    fun testComplaintEtaEstimation() {
        val urgentComplaint = Complaint(
            id = "PF-101",
            priority = "Urgent",
            status = "Assigned",
            complaintType = "Transformer Breakdown"
        )
        assertEquals(20, urgentComplaint.estimatedEtaMinutes())

        val inProgressComplaint = Complaint(
            id = "PF-102",
            priority = "High",
            status = "In Progress"
        )
        assertEquals(15, inProgressComplaint.estimatedEtaMinutes())

        val resolvedComplaint = Complaint(
            id = "PF-103",
            status = "Resolved"
        )
        assertEquals(0, resolvedComplaint.estimatedEtaMinutes())
    }

    @Test
    fun testUserProfileDefaults() {
        val profile = UserProfile(
            uid = "test-uid-123",
            email = "user@powerfix.in",
            name = "Technician Raman"
        )
        assertEquals("customer", profile.role)
        assertEquals("", profile.tnebId)
        assertEquals(true, profile.available)
        assertNotNull(profile.uid)
        assertEquals(false, profile.disabled)
    }

    @Test
    fun testAuthRoleMapping() {
        assertEquals("customer", AuthRole.CUSTOMER.dbValue)
        assertEquals("worker", AuthRole.WORKER.dbValue)
        assertEquals("admin", AuthRole.ADMIN.dbValue)

        assertEquals(AuthRole.CUSTOMER, AuthRole.fromDb("CUSTOMER"))
        assertEquals(AuthRole.WORKER, AuthRole.fromDb("worker"))
        assertEquals(AuthRole.ADMIN, AuthRole.fromDb("Admin"))
        assertNull(AuthRole.fromDb("superuser"))
        assertNull(AuthRole.fromDb(null))

        assertEquals(AuthRole.CUSTOMER, AuthRole.fromDisplay("Customer"))
        assertEquals(AuthRole.WORKER, AuthRole.fromDisplay("Worker"))
        assertNull(AuthRole.fromDisplay("Admin"))
        assertNull(AuthRole.fromDisplay("unknown"))
    }

    @Test
    fun testAuthErrorMessageMapping() {
        assertEquals(
            "Please enter your TNEB Customer ID.",
            AuthError.EMPTY_TNEB_ID.userMessage(AuthRole.CUSTOMER)
        )
        assertEquals(
            "Please enter your TNEB Worker ID.",
            AuthError.EMPTY_TNEB_ID.userMessage(AuthRole.WORKER)
        )
        assertEquals(
            "Invalid TNEB ID format.",
            AuthError.INVALID_TNEB_FORMAT.userMessage(AuthRole.CUSTOMER)
        )
        assertEquals(
            "Customer ID could not be verified.",
            AuthError.TNEB_NOT_FOUND.userMessage(AuthRole.CUSTOMER)
        )
        assertEquals(
            "Worker ID could not be verified.",
            AuthError.TNEB_NOT_FOUND.userMessage(AuthRole.WORKER)
        )
        assertEquals(
            "This TNEB ID is already registered.",
            AuthError.TNEB_ALREADY_REGISTERED.userMessage(AuthRole.CUSTOMER)
        )
        assertEquals(
            "TNEB verification service is currently unavailable.",
            AuthError.TNEB_SERVICE_UNAVAILABLE.userMessage(AuthRole.WORKER)
        )
        assertEquals(
            "Invalid login credentials.",
            AuthError.INVALID_CREDENTIALS.userMessage(AuthRole.CUSTOMER)
        )
        assertEquals(
            "Your session has expired.",
            AuthError.EXPIRED_SESSION.userMessage(AuthRole.WORKER)
        )
    }

    @Test
    fun testTnebIdValidation() {
        assertTrue(TnebIdValidator.isValidCustomerId("12345678901"))
        assertTrue(TnebIdValidator.isValidWorkerId("WK-000123"))
        assertTrue(TnebIdValidator.isValidForRole("worker", "WK-000123"))
        assertTrue(TnebIdValidator.isValidForRole("customer", "12345678901"))

        assertFalse(TnebIdValidator.isValidForRole("worker", ""))
        assertFalse(TnebIdValidator.isValidForRole("customer", ""))
        assertFalse(TnebIdValidator.isValidForRole("worker", "abc"))
        assertFalse(TnebIdValidator.isValidForRole("customer", "ab!c@d"))
        assertFalse(TnebIdValidator.isValidForRole("admin", ""))
    }

    @Test
    fun testEmergencyRequestDefaults() {
        val sos = EmergencyRequest(
            id = "sos-01",
            userId = "test-uid",
            message = "Live cable snapped on street"
        )
        assertEquals("Open", sos.status)
        assertEquals("Live cable snapped on street", sos.message)
    }
}

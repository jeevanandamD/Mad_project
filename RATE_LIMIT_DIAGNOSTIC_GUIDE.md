# Supabase Rate Limiting: Comprehensive Diagnostic & Resolution Guide

## 1. IDENTIFYING THE SOURCE OF RATE LIMIT ERRORS

### 1.1 Determining Where the Error Originates

Rate limit errors can come from multiple sources. Here's how to identify which one:

```kotlin
// Android/Kotlin - Catch and analyze the exception
try {
    supabase.auth.signUpWith(Email) {
        email = userEmail
        password = userPassword
    }
} catch (e: Exception) {
    val errorMessage = e.message ?: ""
    
    when {
        // Auth rate limit (429 on /auth/v1/signup)
        errorMessage.contains("429", ignoreCase = true) -> {
            logError("AUTH_RATE_LIMIT: Registration endpoint rate limited")
        }
        // Database connection limit
        errorMessage.contains("too many connections", ignoreCase = true) -> {
            logError("DB_CONNECTION_LIMIT: PostgreSQL connection pool exhausted")
        }
        // RLS policy timeout or rate limiting
        errorMessage.contains("policy", ignoreCase = true) -> {
            logError("RLS_LIMIT: Row Level Security policy execution limited")
        }
        // Generic API error
        e.toString().contains("HttpRequestTimeoutException") -> {
            logError("TIMEOUT: Possible rate limiting or network issue")
        }
        else -> {
            logError("UNKNOWN: $errorMessage")
        }
    }
}
```

---

## 2. SUPABASE RATE LIMITS BY PLAN & OPERATION

### 2.1 Authentication Rate Limits

| Operation | Free Plan | Pro Plan | Enterprise |
|-----------|-----------|----------|------------|
| **Sign-up attempts** | 3 per 60 sec per IP | 5 per 60 sec per IP | Custom |
| **Sign-in attempts** | 3 per 60 sec per IP | 5 per 60 sec per IP | Custom |
| **Password reset** | 2 per hour per email | 5 per hour per email | Custom |
| **Email confirmation** | 3 per 60 sec per email | 5 per 60 sec per email | Custom |
| **Token refresh** | 10 per 60 sec per user | 15 per 60 sec per user | Custom |
| **Phone verification** | 3 per 60 sec per phone | 5 per 60 sec per phone | Custom |

### 2.2 Database Rate Limits

| Metric | Free Plan | Pro Plan | Enterprise |
|--------|-----------|----------|------------|
| **Concurrent connections** | 2 | 10 | 20+ |
| **Max connection pool size** | Auto-limited | Auto-limited | Custom |
| **Query timeout** | 30 seconds | 30 seconds | Custom |
| **API requests** | 200 req/min | 1000 req/min | Custom |

### 2.3 RLS & Edge Function Limits

- **RLS policy execution**: Each policy has max ~1 second execution time
- **Edge Function execution**: 10 second timeout, with rate limiting per deployment
- **Realtime subscriptions**: Up to 100 concurrent per project on Free plan

---

## 3. DEBUGGING STEPS: LOCATING THE EXACT ERROR

### 3.1 Check Supabase Dashboard Logs

**Path**: Project Dashboard → Logs → Auth Logs / API Logs

```
Steps:
1. Go to Supabase Dashboard
2. Select your project
3. Navigate to "Logs" in the sidebar
4. Filter by Auth logs:
   - Look for 429 status codes
   - Note timestamp of failures
   - Check "error" field for specific messages
5. Look for patterns:
   - Same IP address making repeated attempts
   - Same email attempting signup multiple times
   - Requests from known serverless environments
```

### 3.2 Inspect Network Response Headers

Key headers that reveal rate limiting:

```
Responses headers to inspect:
- Retry-After: <seconds>       // Wait time before retrying
- X-RateLimit-Limit: <number>  // Total requests allowed
- X-RateLimit-Remaining: <n>   // Requests left in current window
- X-RateLimit-Reset: <unix>    // When limit resets
- RateLimit-Reset: <unix>      // Alternative format
- CF-RateLimit-*: *            // Cloudflare rate limiting (if applicable)
```

**Implementation - Kotlin (Android):**

```kotlin
import io.ktor.client.statement.*

suspend fun registerWithRateLimitCheck(
    email: String,
    password: String
): Result<Unit> {
    return try {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        Result.success(Unit)
    } catch (e: Exception) {
        // Access response headers from Ktor client
        if (e is io.ktor.client.network.sockets.ConnectTimeoutException) {
            Result.failure(RateLimitException("Rate limit exceeded", 60))
        } else {
            Result.failure(e)
        }
    }
}

data class RateLimitException(
    val message: String,
    val retryAfterSeconds: Int
) : Exception(message)
```

**Implementation - TypeScript/Node.js:**

```typescript
import { createClient } from '@supabase/supabase-js'

interface RateLimitInfo {
  retryAfter: number
  remaining: number
  limit: number
  reset: Date
}

async function checkAndHandleRateLimit(
  response: Response
): Promise<RateLimitInfo | null> {
  const retryAfter = response.headers.get('Retry-After')
  const remaining = response.headers.get('X-RateLimit-Remaining')
  const limit = response.headers.get('X-RateLimit-Limit')
  const reset = response.headers.get('X-RateLimit-Reset')

  if (response.status === 429) {
    console.warn('Rate limit exceeded', {
      retryAfter: parseInt(retryAfter || '60'),
      remaining: parseInt(remaining || '0'),
      limit: parseInt(limit || '0'),
      resetAt: new Date(parseInt(reset || '0') * 1000),
    })

    return {
      retryAfter: parseInt(retryAfter || '60'),
      remaining: parseInt(remaining || '0'),
      limit: parseInt(limit || '0'),
      reset: new Date(parseInt(reset || '0') * 1000),
    }
  }

  return null
}

async function registerUser(
  email: string,
  password: string
): Promise<{ success: boolean; error?: string }> {
  const supabase = createClient(
    process.env.VITE_SUPABASE_URL!,
    process.env.VITE_SUPABASE_ANON_KEY!
  )

  try {
    const { data, error } = await supabase.auth.signUp({
      email,
      password,
    })

    if (error) {
      if (error.status === 429) {
        return {
          success: false,
          error: `Too many registration attempts. Please wait 60 seconds.`,
        }
      }
      return { success: false, error: error.message }
    }

    return { success: true }
  } catch (err) {
    console.error('Registration error:', err)
    return { success: false, error: 'An unexpected error occurred' }
  }
}
```

### 3.3 HTTP Status Code Analysis

```
429 Too Many Requests
  ├─ Source: Supabase Auth endpoint
  ├─ Indicates: Rate limit on signup/signin/password reset
  ├─ Action: Exponential backoff + user notification
  └─ Duration: Typically 60-300 seconds

503 Service Unavailable
  ├─ Source: Supabase infrastructure
  ├─ Indicates: Server overload or maintenance
  ├─ Action: Exponential backoff + retry
  └─ Duration: Variable

408 Request Timeout
  ├─ Source: Network or RLS evaluation
  ├─ Indicates: Possible slow RLS policy
  ├─ Action: Check policy performance
  └─ Solution: Optimize RLS rules

413 Payload Too Large
  ├─ Source: Request body size limit
  ├─ Indicates: User data exceeds limits
  ├─ Action: Reduce payload size
  └─ Note: Usually ~1MB limit
```

---

## 4. IMMEDIATE MITIGATION STRATEGIES

### 4.1 Exponential Backoff with Jitter (Frontend)

**TypeScript/React Implementation:**

```typescript
interface RetryConfig {
  maxRetries: number
  initialDelayMs: number
  maxDelayMs: number
  backoffMultiplier: number
}

const DEFAULT_RETRY_CONFIG: RetryConfig = {
  maxRetries: 3,
  initialDelayMs: 1000,
  maxDelayMs: 30000,
  backoffMultiplier: 2,
}

async function exponentialBackoffWithJitter<T>(
  fn: () => Promise<T>,
  config: RetryConfig = DEFAULT_RETRY_CONFIG
): Promise<T> {
  let lastError: Error | null = null

  for (let attempt = 0; attempt < config.maxRetries; attempt++) {
    try {
      return await fn()
    } catch (error) {
      lastError = error as Error

      // Don't retry on non-rate-limit errors
      if (!isRateLimitError(error)) {
        throw error
      }

      // Calculate delay with exponential backoff + jitter
      const delay = Math.min(
        config.maxDelayMs,
        config.initialDelayMs * Math.pow(config.backoffMultiplier, attempt)
      )

      // Add jitter: randomize between 0 and calculated delay
      const jitter = Math.random() * delay

      console.warn(
        `Rate limited on attempt ${attempt + 1}. Retrying after ${Math.round(jitter)}ms...`
      )

      await new Promise((resolve) => setTimeout(resolve, jitter))
    }
  }

  throw lastError || new Error('Max retries exceeded')
}

function isRateLimitError(error: unknown): boolean {
  if (error instanceof Error) {
    return (
      error.message.includes('429') ||
      error.message.includes('rate limit') ||
      error.message.includes('too many requests')
    )
  }
  return false
}

// Usage in React
async function handleRegistration(
  email: string,
  password: string,
  onError: (msg: string) => void
) {
  try {
    await exponentialBackoffWithJitter(
      () => registerUser(email, password),
      {
        maxRetries: 5,
        initialDelayMs: 2000,
        maxDelayMs: 60000,
        backoffMultiplier: 1.5,
      }
    )
    onError('') // Clear error on success
  } catch (error) {
    onError((error as Error).message)
  }
}
```

**Kotlin (Android) Implementation:**

```kotlin
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

data class RetryConfig(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 30000,
    val backoffMultiplier: Double = 2.0
)

suspend inline fun <T> exponentialBackoffWithJitter(
    config: RetryConfig = RetryConfig(),
    crossinline fn: suspend () -> T
): T {
    var lastError: Exception? = null

    repeat(config.maxRetries) { attempt ->
        try {
            return fn()
        } catch (e: Exception) {
            lastError = e

            // Only retry on rate limit errors
            if (!isRateLimitError(e)) {
                throw e
            }

            if (attempt < config.maxRetries - 1) {
                // Calculate exponential backoff
                val delay = min(
                    config.maxDelayMs,
                    (config.initialDelayMs * config.backoffMultiplier.pow(attempt)).toLong()
                )

                // Add jitter
                val jitterDelay = (Math.random() * delay).toLong()

                Log.w(
                    "ExponentialBackoff",
                    "Rate limited on attempt ${attempt + 1}. " +
                            "Retrying after ${jitterDelay}ms..."
                )

                delay(jitterDelay)
            }
        }
    }

    throw lastError ?: Exception("Max retries exceeded")
}

private fun isRateLimitError(error: Exception): Boolean {
    return error.message?.contains(
        regex = Regex("(429|rate limit|too many requests)", RegexOption.IGNORE_CASE)
    ) ?: false
}

// Usage in Fragment
viewLifecycleOwner.lifecycleScope.launch {
    try {
        exponentialBackoffWithJitter(
            config = RetryConfig(
                maxRetries = 5,
                initialDelayMs = 2000,
                maxDelayMs = 60000,
                backoffMultiplier = 1.5
            )
        ) {
            supabase.auth.signUpWith(Email) {
                email = userEmail
                password = userPassword
            }
        }
        showSuccessMessage("Registration successful!")
    } catch (e: Exception) {
        showErrorMessage("Registration failed: ${e.message}")
    }
}
```

### 4.2 Client-Side Debouncing

**TypeScript/React:**

```typescript
import { useCallback, useRef } from 'react'

interface DebounceConfig {
  delay: number
  maxWaitMs?: number
}

function useDebounce<T extends (...args: any[]) => Promise<any>>(
  fn: T,
  config: DebounceConfig
) {
  const timeoutRef = useRef<NodeJS.Timeout>()
  const maxWaitTimeoutRef = useRef<NodeJS.Timeout>()
  const lastCallTimeRef = useRef<number>(0)

  const debounced = useCallback(
    async (...args: Parameters<T>) => {
      const now = Date.now()
      lastCallTimeRef.current = now

      // Clear existing timeout
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current)
      }

      // Clear max wait timeout
      if (maxWaitTimeoutRef.current) {
        clearTimeout(maxWaitTimeoutRef.current)
      }

      const execute = () => fn(...args)

      // Regular debounce
      timeoutRef.current = setTimeout(execute, config.delay)

      // Max wait (if specified)
      if (config.maxWaitMs) {
        maxWaitTimeoutRef.current = setTimeout(execute, config.maxWaitMs)
      }
    },
    [fn, config.delay, config.maxWaitMs]
  )

  const cancel = useCallback(() => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current)
    if (maxWaitTimeoutRef.current) clearTimeout(maxWaitTimeoutRef.current)
  }, [])

  return [debounced, cancel] as const
}

// Usage in registration form
function RegistrationForm() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isRegistering, setIsRegistering] = useState(false)
  const [error, setError] = useState('')

  const handleRegister = async () => {
    setIsRegistering(true)
    setError('')

    try {
      await exponentialBackoffWithJitter(
        () => registerUser(email, password),
        { maxRetries: 5, initialDelayMs: 2000, maxDelayMs: 60000 }
      )
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setIsRegistering(false)
    }
  }

  const [debouncedRegister, cancelRegister] = useDebounce(handleRegister, {
    delay: 500, // Prevent rapid successive attempts
    maxWaitMs: 2000, // Force execution after 2 seconds
  })

  return (
    <form onSubmit={(e) => {
      e.preventDefault()
      debouncedRegister()
    }}>
      <input
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="Email"
        disabled={isRegistering}
      />
      <input
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder="Password"
        disabled={isRegistering}
      />
      <button type="submit" disabled={isRegistering}>
        {isRegistering ? 'Registering...' : 'Register'}
      </button>
      {error && <p style={{ color: 'red' }}>{error}</p>}
    </form>
  )
}
```

**Kotlin (Android):**

```kotlin
import kotlinx.coroutines.*

class DebounceHelper {
    private var debounceJob: Job? = null
    private var maxWaitJob: Job? = null

    fun <T> debounce(
        delayMs: Long = 500,
        maxWaitMs: Long? = null,
        scope: CoroutineScope,
        action: suspend () -> T
    ) {
        // Cancel previous debounce
        debounceJob?.cancel()
        maxWaitJob?.cancel()

        // Schedule execution
        debounceJob = scope.launch {
            delay(delayMs)
            action()
        }

        // Force execution after max wait time
        maxWaitMs?.let { wait ->
            maxWaitJob = scope.launch {
                delay(wait)
                if (debounceJob?.isActive == true) {
                    debounceJob?.cancel()
                    action()
                }
            }
        }
    }

    fun cancel() {
        debounceJob?.cancel()
        maxWaitJob?.cancel()
    }
}

// Usage in Fragment
private val debounceHelper = DebounceHelper()

private fun setupRegistrationForm() {
    binding.registerButton.setOnClickListener {
        debounceHelper.debounce(
            delayMs = 500,
            maxWaitMs = 2000,
            scope = viewLifecycleOwner.lifecycleScope
        ) {
            performRegistration()
        }
    }
}

private suspend fun performRegistration() {
    val email = binding.emailInput.text.toString()
    val password = binding.passwordInput.text.toString()

    try {
        exponentialBackoffWithJitter(
            config = RetryConfig(maxRetries = 5)
        ) {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
        }
        showSuccess("Registration successful!")
    } catch (e: Exception) {
        showError("Registration failed: ${e.message}")
    }
}
```

### 4.3 Graceful Error Handling & User Feedback

**TypeScript/React:**

```typescript
interface RateLimitError {
  type: 'RATE_LIMIT'
  retryAfterSeconds: number
  message: string
}

interface RegistrationError {
  type: 'VALIDATION' | 'RATE_LIMIT' | 'AUTH' | 'UNKNOWN'
  message: string
  retryAfterSeconds?: number
  isRetryable: boolean
}

function categorizeError(error: unknown): RegistrationError {
  if (error instanceof Error) {
    const message = error.message.toLowerCase()

    if (message.includes('429') || message.includes('rate limit')) {
      return {
        type: 'RATE_LIMIT',
        message:
          'Too many registration attempts. Please wait a moment and try again.',
        retryAfterSeconds: 60,
        isRetryable: true,
      }
    }

    if (message.includes('invalid email')) {
      return {
        type: 'VALIDATION',
        message: 'Please enter a valid email address.',
        isRetryable: false,
      }
    }

    if (
      message.includes('already registered') ||
      message.includes('user already exists')
    ) {
      return {
        type: 'AUTH',
        message: 'This email is already registered. Try logging in instead.',
        isRetryable: false,
      }
    }

    if (message.includes('weak password')) {
      return {
        type: 'VALIDATION',
        message: 'Password is too weak. Use at least 8 characters.',
        isRetryable: false,
      }
    }
  }

  return {
    type: 'UNKNOWN',
    message:
      'An unexpected error occurred. Please try again later.',
    isRetryable: true,
  }
}

function RateLimitAwareRegistrationForm() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<RegistrationError | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [retryCountdown, setRetryCountdown] = useState(0)

  // Countdown timer for rate limit
  useEffect(() => {
    if (retryCountdown <= 0) return

    const interval = setInterval(() => {
      setRetryCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(interval)
          return 0
        }
        return prev - 1
      })
    }, 1000)

    return () => clearInterval(interval)
  }, [retryCountdown])

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setIsLoading(true)

    try {
      // Validate inputs first
      if (!email || !password) {
        throw new Error('Please fill in all fields')
      }

      if (password.length < 8) {
        throw new Error('weak password')
      }

      // Attempt registration with retry
      await exponentialBackoffWithJitter(
        () => registerUser(email, password),
        { maxRetries: 3, initialDelayMs: 1000 }
      )

      // Success
      alert('Registration successful! Please check your email.')
      setEmail('')
      setPassword('')
    } catch (err) {
      const categorizedError = categorizeError(err)
      setError(categorizedError)

      if (categorizedError.type === 'RATE_LIMIT') {
        setRetryCountdown(categorizedError.retryAfterSeconds || 60)
      }
    } finally {
      setIsLoading(false)
    }
  }

  const isRetryDisabled =
    retryCountdown > 0 || isLoading || !email || !password

  return (
    <form onSubmit={handleRegister}>
      <div>
        <input
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="Email"
          disabled={isLoading}
        />
      </div>

      <div>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Password"
          disabled={isLoading}
        />
      </div>

      {error && (
        <div
          style={{
            padding: '10px',
            marginBottom: '10px',
            backgroundColor:
              error.type === 'RATE_LIMIT' ? '#fff3cd' : '#f8d7da',
            color: error.type === 'RATE_LIMIT' ? '#856404' : '#721c24',
            borderRadius: '4px',
          }}
        >
          <strong>
            {error.type === 'RATE_LIMIT' ? '⏱️ Rate Limited' : '❌ Error'}
          </strong>
          <p>{error.message}</p>

          {retryCountdown > 0 && (
            <p style={{ margin: '10px 0 0 0', fontSize: '14px' }}>
              Please wait {retryCountdown} seconds before trying again.
            </p>
          )}
        </div>
      )}

      <button type="submit" disabled={isRetryDisabled}>
        {isLoading
          ? 'Registering...'
          : retryCountdown > 0
            ? `Retry in ${retryCountdown}s`
            : 'Register'}
      </button>
    </form>
  )
}
```

**Kotlin (Android):**

```kotlin
sealed class RegistrationError(open val message: String) {
    data class RateLimit(
        override val message: String = "Too many registration attempts. Please wait.",
        val retryAfterSeconds: Int = 60
    ) : RegistrationError(message)

    data class Validation(override val message: String) : RegistrationError(message)

    data class AuthError(override val message: String) : RegistrationError(message)

    data class Unknown(override val message: String) : RegistrationError(message)

    val isRetryable: Boolean
        get() = this is RateLimit || this is Unknown
}

fun categorizeError(error: Exception): RegistrationError {
    val message = error.message?.lowercase() ?: ""

    return when {
        message.contains("429") || message.contains("rate limit") ->
            RegistrationError.RateLimit(
                message = "Too many registration attempts. Please wait $60 seconds.",
                retryAfterSeconds = 60
            )

        message.contains("invalid email") ->
            RegistrationError.Validation("Please enter a valid email address.")

        message.contains("already registered") || message.contains("already exists") ->
            RegistrationError.AuthError("This email is already registered.")

        message.contains("weak password") ->
            RegistrationError.Validation(
                "Password is too weak. Use at least 8 characters."
            )

        else -> RegistrationError.Unknown("Registration failed. Please try again.")
    }
}

class RegistrationViewModel : ViewModel() {
    private val _state = MutableLiveData<RegistrationState>()
    val state: LiveData<RegistrationState> = _state

    private var retryCountdownJob: Job? = null

    fun register(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = RegistrationState.Error(
                RegistrationError.Validation("Please fill in all fields")
            )
            return
        }

        _state.value = RegistrationState.Loading

        viewModelScope.launch {
            try {
                exponentialBackoffWithJitter(
                    config = RetryConfig(maxRetries = 3)
                ) {
                    supabase.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                    }
                }

                _state.value = RegistrationState.Success
            } catch (e: Exception) {
                val categorized = categorizeError(e)
                _state.value = RegistrationState.Error(categorized)

                if (categorized is RegistrationError.RateLimit) {
                    startRetryCountdown(categorized.retryAfterSeconds)
                }
            }
        }
    }

    private fun startRetryCountdown(seconds: Int) {
        retryCountdownJob?.cancel()

        var remaining = seconds
        retryCountdownJob = viewModelScope.launch {
            while (remaining > 0) {
                _state.value = RegistrationState.RateLimited(remaining)
                delay(1000)
                remaining--
            }
        }
    }

    sealed class RegistrationState {
        object Loading : RegistrationState()
        object Success : RegistrationState()
        data class Error(val error: RegistrationError) : RegistrationState()
        data class RateLimited(val secondsRemaining: Int) : RegistrationState()
    }
}
```

---

## 5. DATABASE-SPECIFIC CONSIDERATIONS

### 5.1 Checking for Expensive RLS Policies

RLS policies that execute complex queries can become rate-limited bottlenecks.

**Diagnostic SQL:**

```sql
-- Check which tables have RLS enabled
SELECT
    schemaname,
    tablename,
    rowsecurity
FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
ORDER BY tablename;

-- View all RLS policies
SELECT
    schemaname,
    tablename,
    policyname,
    permissive,
    roles,
    qual,
    with_check
FROM pg_policies
WHERE schemaname NOT IN ('pg_catalog', 'information_schema');

-- Check for slow policy evaluations
-- (Enable pgAudit in Supabase settings)
SELECT
    query,
    mean_exec_time,
    calls,
    total_exec_time
FROM pg_stat_statements
WHERE query ILIKE '%RLS%' OR query ILIKE '%policy%'
ORDER BY mean_exec_time DESC;
```

### 5.2 Optimize RLS Policies for Registration

**❌ SLOW - Inefficient Policy:**

```sql
-- This policy queries related tables for every single request
CREATE POLICY "users_can_view_own_profile" ON public.profiles
    FOR SELECT
    USING (
        auth.uid() = id
        AND EXISTS (
            SELECT 1 FROM public.audit_log
            WHERE user_id = auth.uid()
            AND action = 'profile_viewed'
            AND created_at > NOW() - INTERVAL '1 day'
        )
    );
```

**✅ FAST - Optimized Policy:**

```sql
-- Simpler policy using indexed columns
CREATE POLICY "users_can_view_own_profile" ON public.profiles
    FOR SELECT
    USING (auth.uid() = id);

-- Create index on frequently queried columns
CREATE INDEX idx_profiles_auth_uid ON public.profiles(id)
WHERE id = auth.uid();
```

### 5.3 Batch Operations to Reduce API Calls

**❌ Multiple API Calls (Triggers Rate Limit):**

```typescript
async function registerUserWithDetails(
  email: string,
  password: string,
  profile: UserProfile
): Promise<void> {
  // Call 1: Auth signup
  const { data, error: authError } = await supabase.auth.signUp({
    email,
    password,
  })

  if (authError) throw authError
  const userId = data.user!.id

  // Call 2: Insert profile
  const { error: profileError } = await supabase
    .from('profiles')
    .insert({ id: userId, ...profile })

  if (profileError) throw profileError

  // Call 3: Insert settings
  const { error: settingsError } = await supabase
    .from('user_settings')
    .insert({ user_id: userId, theme: 'light' })

  if (settingsError) throw settingsError
}
```

**✅ Batched with Edge Function:**

```typescript
// Frontend
async function registerUserWithDetails(
  email: string,
  password: string,
  profile: UserProfile
): Promise<void> {
  // Single call to Edge Function
  const response = await fetch(
    `${process.env.VITE_SUPABASE_URL}/functions/v1/register-user`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${process.env.VITE_SUPABASE_ANON_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, password, profile }),
    }
  )

  if (!response.ok) {
    throw new Error(`Registration failed: ${response.statusText}`)
  }
}
```

**Edge Function (TypeScript):**

```typescript
// supabase/functions/register-user/index.ts
import { createClient } from '@supabase/supabase-js'

interface RegistrationRequest {
  email: string
  password: string
  profile: {
    full_name: string
    avatar_url?: string
  }
}

Deno.serve(async (req) => {
  if (req.method !== 'POST') {
    return new Response('Method not allowed', { status: 405 })
  }

  const { email, password, profile } = (await req.json()) as RegistrationRequest

  const supabase = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')! // Service role for elevated permissions
  )

  try {
    // Step 1: Create auth user
    const { data: authData, error: authError } = await supabase.auth.admin.createUser({
      email,
      password,
      email_confirm: false,
    })

    if (authError) {
      throw new Error(`Auth error: ${authError.message}`)
    }

    const userId = authData.user!.id

    // Step 2 & 3: Batch insert profile and settings
    const { error: dataError } = await supabase
      .from('profiles')
      .insert({
        id: userId,
        email,
        ...profile,
      })

    if (dataError) {
      // Rollback: delete user if profile insert fails
      await supabase.auth.admin.deleteUser(userId)
      throw new Error(`Profile error: ${dataError.message}`)
    }

    return new Response(
      JSON.stringify({ success: true, userId }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: (error as Error).message }),
      { status: 400, headers: { 'Content-Type': 'application/json' } }
    )
  }
})
```

---

## 6. LONG-TERM ARCHITECTURAL IMPROVEMENTS

### 6.1 Queue-Based Registration Processing

**Using Bull Queue with Node.js:**

```typescript
import Queue from 'bull'
import { createClient } from '@supabase/supabase-js'

interface RegistrationTask {
  email: string
  password: string
  profile: UserProfile
}

const registrationQueue = new Queue<RegistrationTask>(
  'user-registration',
  process.env.REDIS_URL!
)

// Configure concurrency to prevent rate limiting
registrationQueue.process(1, async (job) => {
  const { email, password, profile } = job.data
  const supabase = createClient(
    process.env.SUPABASE_URL!,
    process.env.SUPABASE_SERVICE_ROLE_KEY!
  )

  try {
    // Register with retry
    const { data, error } = await supabase.auth.admin.createUser({
      email,
      password,
      email_confirm: false,
    })

    if (error) throw error

    // Insert profile
    const { error: profileError } = await supabase
      .from('profiles')
      .insert({ id: data.user!.id, ...profile })

    if (profileError) throw profileError

    return { success: true, userId: data.user!.id }
  } catch (error) {
    // Retry up to 3 times
    if (job.attemptsMade < 3) {
      throw error
    }
    // Failed permanently
    console.error(`Failed to register ${email}:`, error)
    throw error
  }
})

// Handle failed jobs
registrationQueue.on('failed', async (job, err) => {
  console.error(`Job ${job.id} failed:`, err.message)
  // Send notification to user
})

// API endpoint
app.post('/api/register', async (req, res) => {
  try {
    const job = await registrationQueue.add(
      {
        email: req.body.email,
        password: req.body.password,
        profile: req.body.profile,
      },
      {
        attempts: 3,
        backoff: {
          type: 'exponential',
          delay: 2000,
        },
      }
    )

    res.status(202).json({
      message: 'Registration queued',
      jobId: job.id,
    })
  } catch (error) {
    res.status(400).json({ error: (error as Error).message })
  }
})
```

### 6.2 Implement Monitoring & Alerting

**Supabase Logs Integration:**

```typescript
// Monitor rate limit events
import * as pg from 'pg'

async function monitorRateLimits() {
  const client = new pg.Client({
    connectionString: process.env.DATABASE_URL!,
  })

  await client.connect()

  // Subscribe to rate limit errors (requires pgAudit)
  const result = await client.query(`
    SELECT
      timestamp,
      error_code,
      error_message,
      client_ip,
      COUNT(*) as occurrence_count
    FROM pg_stat_activity
    WHERE state_change > NOW() - INTERVAL '1 hour'
    GROUP BY error_code, error_message, client_ip, timestamp
    HAVING error_code IN ('SQLSTATE[42P01]')  -- Rate limit state
    ORDER BY timestamp DESC
  `)

  return result.rows
}

// Send to monitoring service (e.g., Sentry, Datadog)
import Sentry from '@sentry/node'

async function detectAndAlertRateLimits() {
  const rateLimitEvents = await monitorRateLimits()

  rateLimitEvents.forEach((event) => {
    if (event.occurrence_count > 10) {
      Sentry.captureMessage('High rate limit occurrences detected', 'warning', {
        tags: {
          event_type: 'rate_limit_spike',
          client_ip: event.client_ip,
        },
        extra: {
          count: event.occurrence_count,
          error: event.error_message,
        },
      })
    }
  })
}

// Run monitoring every 5 minutes
setInterval(detectAndAlertRateLimits, 5 * 60 * 1000)
```

### 6.3 Request Rate Limit Increases

**When to request from Supabase:**

```markdown
Contact Supabase Support at: support@supabase.io

Include:
- Project ID
- Description of rate limit issue
- Current traffic patterns
- Requested new limits
- Business justification

Example:
"We're experiencing 429 errors on signup. Our app serves
1000+ users/day, with peak signup times of 50 req/minute.
Current Free plan limits of 3 req/60s per IP are insufficient.
Request: Increase to 50 req/60s for our IPs."
```

---

## 7. COMPREHENSIVE IMPLEMENTATION GUIDE

### 7.1 Complete Android Implementation

**File: `RegisterFragment.kt`**

```kotlin
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.sucs.AppContainer
import com.example.sucs.databinding.FragmentRegisterBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.pow

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private var registrationAttempts = 0
    private var lastAttemptTime = 0L
    private val MIN_TIME_BETWEEN_ATTEMPTS = 500L // Debounce

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.registerButton.setOnClickListener {
            handleRegisterClick()
        }
    }

    private fun handleRegisterClick() {
        // Debounce rapid clicks
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAttemptTime < MIN_TIME_BETWEEN_ATTEMPTS) {
            return
        }
        lastAttemptTime = currentTime

        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()
        val confirmPassword = binding.confirmPasswordInput.text.toString()

        // Validation
        val error = validateRegistrationInput(email, password, confirmPassword)
        if (error != null) {
            displayError(error, isRetryable = false)
            return
        }

        performRegistration(email, password)
    }

    private fun validateRegistrationInput(
        email: String,
        password: String,
        confirmPassword: String
    ): String? {
        return when {
            email.isBlank() -> "Email is required"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                "Please enter a valid email"
            password.isBlank() -> "Password is required"
            password.length < 8 -> "Password must be at least 8 characters"
            password != confirmPassword -> "Passwords do not match"
            else -> null
        }
    }

    private fun performRegistration(email: String, password: String) {
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                exponentialBackoffWithJitter(
                    config = RetryConfig(
                        maxRetries = 4,
                        initialDelayMs = 1500,
                        maxDelayMs = 30000,
                        backoffMultiplier = 1.5
                    )
                ) {
                    AppContainer.supabase.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                    }
                }

                setLoading(false)
                displaySuccess("Registration successful! Check your email for confirmation.")
                binding.emailInput.text?.clear()
                binding.passwordInput.text?.clear()
                binding.confirmPasswordInput.text?.clear()

            } catch (e: Exception) {
                setLoading(false)
                val categorized = categorizeRegistrationError(e)
                displayError(categorized.message, categorized.isRetryable)

                if (categorized is RegistrationErrorCategory.RateLimit) {
                    displayRateLimitCountdown(categorized.retryAfterSeconds)
                }
            }
        }
    }

    private suspend fun exponentialBackoffWithJitter(
        config: RetryConfig,
        operation: suspend () -> Unit
    ) {
        var lastError: Exception? = null

        repeat(config.maxRetries) { attempt ->
            try {
                operation()
                return // Success
            } catch (e: Exception) {
                lastError = e

                if (!isRateLimitError(e) || attempt == config.maxRetries - 1) {
                    throw e
                }

                // Calculate delay
                val baseDelay = (config.initialDelayMs * 
                    config.backoffMultiplier.pow(attempt)).toLong()
                val delay = min(config.maxDelayMs, baseDelay)
                val jitteredDelay = (Math.random() * delay).toLong()

                android.util.Log.w(
                    "RegisterFragment",
                    "Rate limited on attempt ${attempt + 1}. " +
                    "Retrying after ${jitteredDelay}ms..."
                )

                delay(jitteredDelay)
            }
        }

        throw lastError ?: Exception("Registration failed")
    }

    private fun isRateLimitError(error: Exception): Boolean {
        return error.message?.contains(
            Regex("429|rate limit|too many requests", RegexOption.IGNORE_CASE)
        ) ?: false
    }

    private fun categorizeRegistrationError(
        error: Exception
    ): RegistrationErrorCategory {
        val message = error.message?.lowercase() ?: ""

        return when {
            message.contains("429") || message.contains("rate limit") ->
                RegistrationErrorCategory.RateLimit(
                    "Too many registration attempts. Please wait 60 seconds.",
                    60
                )
            message.contains("invalid email") ->
                RegistrationErrorCategory.Validation("Invalid email format")
            message.contains("already registered") ->
                RegistrationErrorCategory.Auth("This email is already registered")
            message.contains("weak password") ->
                RegistrationErrorCategory.Validation(
                    "Password is too weak"
                )
            else ->
                RegistrationErrorCategory.Unknown("Registration failed. Please try again.")
        }
    }

    private fun displayError(message: String, isRetryable: Boolean) {
        binding.errorMessage.apply {
            text = message
            visibility = View.VISIBLE
            setTextColor(
                if (isRetryable)
                    android.graphics.Color.parseColor("#856404")
                else
                    android.graphics.Color.parseColor("#721c24")
            )
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun displaySuccess(message: String) {
        binding.errorMessage.apply {
            text = message
            visibility = View.VISIBLE
            setTextColor(android.graphics.Color.parseColor("#155724"))
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun displayRateLimitCountdown(seconds: Int) {
        var remaining = seconds

        viewLifecycleOwner.lifecycleScope.launch {
            while (remaining > 0) {
                binding.registerButton.text = "Retry in ${remaining}s"
                binding.registerButton.isEnabled = false

                delay(1000)
                remaining--
            }

            binding.registerButton.apply {
                text = "Register"
                isEnabled = true
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.registerButton.isEnabled = !isLoading
        binding.emailInput.isEnabled = !isLoading
        binding.passwordInput.isEnabled = !isLoading
        binding.confirmPasswordInput.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Supporting data classes
data class RetryConfig(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 30000,
    val backoffMultiplier: Double = 2.0
)

sealed class RegistrationErrorCategory(open val message: String) {
    data class RateLimit(
        override val message: String,
        val retryAfterSeconds: Int
    ) : RegistrationErrorCategory(message)

    data class Validation(override val message: String) :
        RegistrationErrorCategory(message)

    data class Auth(override val message: String) :
        RegistrationErrorCategory(message)

    data class Unknown(override val message: String) :
        RegistrationErrorCategory(message)

    val isRetryable: Boolean get() = this is RateLimit
}
```

---

## 8. QUICK REFERENCE: RATE LIMIT CODES

| Code | Meaning | Action |
|------|---------|--------|
| 429 | Rate limit exceeded | Exponential backoff + retry |
| 430 | Authentication rate limit | Same email/IP limit reached |
| 503 | Service unavailable | Backoff + retry |
| 408 | Request timeout | Possible slow RLS, check DB |
| 413 | Payload too large | Reduce request size |

---

## 9. DEBUGGING CHECKLIST

- [ ] Check Supabase Auth logs for 429 errors
- [ ] Verify IP rate limits (especially in shared/serverless environments)
- [ ] Review RLS policies for expensive queries
- [ ] Check trigger functions for excessive database calls
- [ ] Monitor registration trigger frequency
- [ ] Implement exponential backoff retry logic
- [ ] Add client-side debouncing to forms
- [ ] Set up monitoring/alerting for rate limit spikes
- [ ] Test with gradual load increase
- [ ] Contact Supabase support if rate limits are insufficient

---

## Conclusion

Rate limiting issues are typically caused by:
1. **Naive retry logic** - Immediate retries compound the problem
2. **Expensive RLS policies** - Slow policy evaluation
3. **Shared IP addresses** - In serverless/containerized environments
4. **Insufficient plan limits** - For high-traffic applications

The solutions involve exponential backoff, optimized RLS policies, and potentially using Supabase Edge Functions or queue systems for large-scale registration flows.

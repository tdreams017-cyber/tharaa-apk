package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object FirestoreManager {
    private const val TAG = "FirestoreManager"

    // Check if Firebase is available at runtime (has google-services.json and initialized)
    val isFirebaseAvailable: Boolean by lazy {
        try {
            val app = FirebaseApp.getInstance()
            app != null
        } catch (e: Exception) {
            Log.w(TAG, "Firebase App is not initialized. Falling back to robust local simulation.")
            false
        }
    }

    private val auth: FirebaseAuth? by lazy {
        if (isFirebaseAvailable) FirebaseAuth.getInstance() else null
    }

    private val firestore: FirebaseFirestore? by lazy {
        if (isFirebaseAvailable) FirebaseFirestore.getInstance() else null
    }

    // Auth State flows
    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    // Simulated local user state if Firebase is absent
    private val _simulatedUser = MutableStateFlow<SimulatedUser?>(null)
    val simulatedUser: StateFlow<SimulatedUser?> = _simulatedUser

    init {
        if (isFirebaseAvailable) {
            auth?.addAuthStateListener { firebaseAuth ->
                _currentUser.value = firebaseAuth.currentUser
                if (firebaseAuth.currentUser != null) {
                    // Sync from Firestore
                    fetchUserData(firebaseAuth.currentUser!!.uid) { /* sync callback */ }
                }
            }
        }
    }

    data class SimulatedUser(
        val uid: String,
        val displayName: String,
        val email: String,
        val photoUrl: String? = null
    )

    // User Data container that holds bookmarks, notes, highlights, and score
    data class UserBookData(
        var lastReadIndex: Int = 0,
        var bookmarks: List<Int> = emptyList(),
        var notes: Map<String, String> = emptyMap(), // key: "chapterId_sectionIndex" or "chapterId_general"
        var highlights: Map<String, String> = emptyMap(), // key: "chapterId_sectionIndex", value: colorHex (e.g. "#FFEB3B")
        var quizScore: Int = 0,
        var fontScale: Float = 1.0f,
        var isDarkMode: Boolean = false,
        var fontColorIndex: Int = 0
    )

    private val _userBookData = MutableStateFlow(UserBookData())
    val userBookData: StateFlow<UserBookData> = _userBookData

    // Load initial data from local preferences
    fun loadLocalData(context: Context) {
        val sharedPref = context.getSharedPreferences("wealth_book_prefs", Context.MODE_PRIVATE)
        val lastRead = sharedPref.getInt("last_read_index", 0)
        val fontScaleValue = sharedPref.getFloat("font_size_scale", 1.0f)
        val isDark = sharedPref.getBoolean("is_dark_mode", false)
        val fontColorIdx = sharedPref.getInt("font_color_index", 0)
        val score = sharedPref.getInt("quiz_high_score", 0)

        val bookmarkedIds = sharedPref.getString("bookmarked_ids", "")
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?.mapNotNull { it.toIntOrNull() } ?: emptyList()

        // Read notes
        val notesMap = mutableMapOf<String, String>()
        sharedPref.all.forEach { (key, value) ->
            if (key.startsWith("note_") && value is String) {
                notesMap[key.removePrefix("note_")] = value
            }
        }

        // Read highlights
        val highlightsMap = mutableMapOf<String, String>()
        sharedPref.all.forEach { (key, value) ->
            if (key.startsWith("hl_") && value is String) {
                highlightsMap[key.removePrefix("hl_")] = value
            }
        }

        _userBookData.value = UserBookData(
            lastReadIndex = lastRead,
            bookmarks = bookmarkedIds,
            notes = notesMap,
            highlights = highlightsMap,
            quizScore = score,
            fontScale = fontScaleValue,
            isDarkMode = isDark,
            fontColorIndex = fontColorIdx
        )

        // Check if there is a simulated logged in user
        val simUid = sharedPref.getString("sim_uid", null)
        if (simUid != null) {
            val simName = sharedPref.getString("sim_name", "مستثمر طموح") ?: "مستثمر طموح"
            val simEmail = sharedPref.getString("sim_email", "pioneer@wealth.com") ?: "pioneer@wealth.com"
            _simulatedUser.value = SimulatedUser(simUid, simName, simEmail)
        }
    }

    // Save user data (to both Local Preferences and Firestore if signed in)
    fun saveUserData(context: Context, update: UserBookData.() -> Unit) {
        val current = _userBookData.value
        current.update()
        _userBookData.value = current.copy() // trigger flow update

        // Persist locally
        val sharedPref = context.getSharedPreferences("wealth_book_prefs", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        
        editor.putInt("last_read_index", current.lastReadIndex)
        editor.putFloat("font_size_scale", current.fontScale)
        editor.putBoolean("is_dark_mode", current.isDarkMode)
        editor.putInt("font_color_index", current.fontColorIndex)
        editor.putInt("quiz_high_score", current.quizScore)
        editor.putString("bookmarked_ids", current.bookmarks.joinToString(","))

        // Save notes
        current.notes.forEach { (key, value) ->
            editor.putString("note_$key", value)
        }
        // Save highlights
        current.highlights.forEach { (key, value) ->
            editor.putString("hl_$key", value)
        }
        
        editor.apply()

        // Sync to cloud Firestore if online/authenticated
        syncToCloud()
    }

    private fun syncToCloud() {
        val uid = auth?.currentUser?.uid ?: _simulatedUser.value?.uid ?: return
        if (!isFirebaseAvailable) {
            Log.d(TAG, "Simulated cloud sync for user: $uid")
            return
        }

        val dataMap = mapOf(
            "lastReadIndex" to _userBookData.value.lastReadIndex,
            "bookmarks" to _userBookData.value.bookmarks,
            "notes" to _userBookData.value.notes,
            "highlights" to _userBookData.value.highlights,
            "quizScore" to _userBookData.value.quizScore,
            "fontScale" to _userBookData.value.fontScale,
            "isDarkMode" to _userBookData.value.isDarkMode,
            "fontColorIndex" to _userBookData.value.fontColorIndex
        )

        firestore?.collection("users")?.document(uid)
            ?.set(dataMap)
            ?.addOnSuccessListener {
                Log.d(TAG, "User data successfully synced to Firestore.")
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Error syncing to Firestore: ", e)
            }
    }

    private fun fetchUserData(uid: String, onComplete: () -> Unit) {
        if (!isFirebaseAvailable) return
        firestore?.collection("users")?.document(uid)?.get()
            ?.addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val lastRead = document.getLong("lastReadIndex")?.toInt() ?: 0
                    val bookmarks = (document.get("bookmarks") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() } ?: emptyList()
                    val notes = (document.get("notes") as? Map<*, *>)?.map { it.key.toString() to it.value.toString() }?.toMap() ?: emptyMap()
                    val highlights = (document.get("highlights") as? Map<*, *>)?.map { it.key.toString() to it.value.toString() }?.toMap() ?: emptyMap()
                    val quizScore = document.getLong("quizScore")?.toInt() ?: 0
                    val fontScale = document.getDouble("fontScale")?.toFloat() ?: 1.0f
                    val isDarkMode = document.getBoolean("isDarkMode") ?: false
                    val fontColorIndex = document.getLong("fontColorIndex")?.toInt() ?: 0

                    _userBookData.value = UserBookData(
                        lastReadIndex = lastRead,
                        bookmarks = bookmarks,
                        notes = notes,
                        highlights = highlights,
                        quizScore = quizScore,
                        fontScale = fontScale,
                        isDarkMode = isDarkMode,
                        fontColorIndex = fontColorIndex
                    )
                }
                onComplete()
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Error fetching from Firestore: ", e)
                onComplete()
            }
    }

    // --- GOOGLE SIGN IN & AUTH ACTIONS ---

    fun loginSimulatedUser(context: Context, name: String, email: String) {
        val uid = "sim_" + System.currentTimeMillis()
        val user = SimulatedUser(uid, name, email)
        _simulatedUser.value = user

        val sharedPref = context.getSharedPreferences("wealth_book_prefs", Context.MODE_PRIVATE)
        sharedPref.edit()
            .putString("sim_uid", uid)
            .putString("sim_name", name)
            .putString("sim_email", email)
            .apply()

        // Sync local data with this simulated cloud profile
        syncToCloud()
    }

    fun logoutUser(context: Context) {
        if (isFirebaseAvailable) {
            auth?.signOut()
        }
        _simulatedUser.value = null
        _currentUser.value = null

        val sharedPref = context.getSharedPreferences("wealth_book_prefs", Context.MODE_PRIVATE)
        sharedPref.edit()
            .remove("sim_uid")
            .remove("sim_name")
            .remove("sim_email")
            .apply()

        // Reset data flow
        _userBookData.value = UserBookData()
    }
}

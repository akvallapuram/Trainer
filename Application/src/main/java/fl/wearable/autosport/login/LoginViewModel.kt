package fl.wearable.autosport.login

import androidx.lifecycle.ViewModel
import fl.wearable.autosport.BuildConfig.SYFT_AUTH_TOKEN

/*
    The ViewModel class is designed to store and manage UI-related data in a lifecycle conscious way. 
    The ViewModel class allows data to survive configuration changes such as screen rotations.
 */
class LoginViewModel : ViewModel() {

    fun checkUrl(baseUrl: String): Boolean {
        return true
    }

    fun getAuthToken() : String = SYFT_AUTH_TOKEN
}
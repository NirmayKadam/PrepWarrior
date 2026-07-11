// Firebase configuration fallback - Replace with actual credentials
const firebaseConfig = {
  apiKey: "AIzaSyAqYPfeBuHJdxdU5PXxXyYrBO46WvILOkg",
  authDomain: "prepwarrior-467c4.firebaseapp.com",
  projectId: "prepwarrior-467c4",
  storageBucket: "prepwarrior-467c4.firebasestorage.app",
  messagingSenderId: "525786522065",
  appId: "1:525786522065:web:8fc2e89708ee575beac0f0",
  measurementId: "G-TMQQLPW67T"
};

// Initialize Firebase
if (typeof firebase !== 'undefined' && !firebase.apps.length) {
    firebase.initializeApp(firebaseConfig);
}

// Redirect helpers
const isLoginPage = window.location.pathname === '/' || window.location.pathname.endsWith('index.html');

if (typeof firebase !== 'undefined') {
    firebase.auth().onAuthStateChanged((user) => {
        if (!user && !isLoginPage) {
            // Redirect to login if not authenticated and not on login page
            window.location.href = '/';
        } else if (user && isLoginPage) {
            // Redirect to dashboard if logged in and on login page
            window.location.href = '/dashboard';
        }
    });
}

// Auth wrappers
async function signUp(email, password) {
    try {
        const userCredential = await firebase.auth().createUserWithEmailAndPassword(email, password);
        return { success: true, user: userCredential.user };
    } catch (error) {
        return { success: false, error: error.message };
    }
}

async function signIn(email, password) {
    try {
        const userCredential = await firebase.auth().signInWithEmailAndPassword(email, password);
        return { success: true, user: userCredential.user };
    } catch (error) {
        return { success: false, error: error.message };
    }
}

async function logOut() {
    try {
        await firebase.auth().signOut();
        window.location.href = '/';
    } catch (error) {
        console.error("Logout failed:", error);
    }
}

async function signInWithGoogle() {
    try {
        const provider = new firebase.auth.GoogleAuthProvider();
        const result = await firebase.auth().signInWithPopup(provider);
        return { success: true, user: result.user };
    } catch (error) {
        return { success: false, error: error.message };
    }
}

// Authenticated fetch helper - Automatically attaches Firebase ID token
async function authenticatedFetch(url, options = {}) {
    const user = firebase.auth().currentUser;
    if (!user) {
        // Fallback if token is requested before state is resolved (or user is logged out)
        throw new Error("User not authenticated");
    }
    
    const token = await user.getIdToken();
    
    // Set up headers
    options.headers = options.headers || {};
    options.headers['Authorization'] = `Bearer ${token}`;
    if (!(options.body instanceof FormData)) {
        options.headers['Content-Type'] = options.headers['Content-Type'] || 'application/json';
    }
    
    const response = await fetch(url, options);
    
    if (response.status === 401) {
        // Unauthorized -> sign out locally and redirect
        firebase.auth().signOut();
        window.location.href = '/';
    }
    
    return response;
}

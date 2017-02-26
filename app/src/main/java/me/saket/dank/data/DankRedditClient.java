package me.saket.dank.data;

import android.content.Context;
import android.os.Looper;
import android.support.annotation.NonNull;

import net.dean.jraw.RedditClient;
import net.dean.jraw.auth.AuthenticationManager;
import net.dean.jraw.auth.AuthenticationState;
import net.dean.jraw.auth.RefreshTokenHandler;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.SubmissionRequest;
import net.dean.jraw.http.oauth.Credentials;
import net.dean.jraw.http.oauth.OAuthData;
import net.dean.jraw.http.oauth.OAuthHelper;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Subreddit;
import net.dean.jraw.paginators.SubredditPaginator;
import net.dean.jraw.paginators.UserSubredditsPaginator;

import java.util.List;

import me.saket.dank.R;
import me.saket.dank.di.Dank;
import me.saket.dank.utils.AndroidTokenStore;
import rx.Observable;
import rx.functions.Func1;
import timber.log.Timber;

/**
 * Wrapper around {@link RedditClient}.
 */
public class DankRedditClient {

    private final Context context;
    private final String redditAppClientId;
    private final RedditClient redditClient;
    private final AuthenticationManager redditAuthManager;
    private Credentials loginCredentials;

    private boolean authManagerInitialized;

    public DankRedditClient(Context context, RedditClient redditClient, AuthenticationManager redditAuthManager) {
        this.context = context;
        this.redditClient = redditClient;
        this.redditAuthManager = redditAuthManager;
        this.redditAppClientId = context.getString(R.string.reddit_app_client_id);
    }

    public SubredditPaginator frontPagePaginator() {
        return new SubredditPaginator(redditClient, "pics");
    }

    /**
     * Get all details of submissions, including comments.
     */
    public Submission fullSubmissionData(String submissionId) {
        return redditClient.getSubmission(new SubmissionRequest.Builder(submissionId).build());
    }

    /**
     * Subreddits user has subscribed to.
     */
    public Observable<List<Subreddit>> userSubreddits() {
        return Observable.fromCallable(() -> {
            UserSubredditsPaginator subredditsPaginator = new UserSubredditsPaginator(redditClient, "subscriber");
            return subredditsPaginator.accumulateMergedAllSorted();
        });
    }

// ======== AUTHENTICATION ======== //

    /**
     * Get a new API token if we haven't already or refresh the existing API token if the last one has expired.
     */
    public Observable<Boolean> authenticateIfNeeded() {
        return Observable.fromCallable(() -> {
            if (!authManagerInitialized) {
                redditAuthManager.init(redditClient, new RefreshTokenHandler(new AndroidTokenStore(context), redditClient));
                authManagerInitialized = true;
            }

            // TODO: 10/02/17 Update this code for logged in user.

            AuthenticationState authState = redditAuthManager.checkAuthState();
            if (authState != AuthenticationState.READY) {
                Credentials credentials = Credentials.userlessApp(redditAppClientId, Dank.sharedPrefs().getDeviceUuid());

                switch (authState) {
                    case NONE:
                        //Timber.d("Authenticating app");
                        redditClient.authenticate(redditClient.getOAuthHelper().easyAuth(credentials));
                        break;

                    case NEED_REFRESH:
                        //Timber.d("Refreshing token");
                        redditAuthManager.refreshAccessToken(credentials);
                        break;
                }
            } else {
                //Timber.d("Already authenticated");
            }

            return true;
        });
    }

    public String loggedInUserName() {
        return redditClient.getAuthenticatedUser();
    }

    public boolean isUserLoggedIn() {
        boolean hasActiveUserContext = redditClient.hasActiveUserContext();
        Timber.i("hasActiveUserContext: %s", hasActiveUserContext);
        boolean authenticated = redditClient.isAuthenticated();
        Timber.i("authenticated: %s", authenticated);
        return authenticated && hasActiveUserContext;
    }

    public UserLoginHelper userLoginHelper() {
        return new UserLoginHelper();
    }

    @NonNull
    public Func1<Observable<? extends Throwable>, Observable<?>> refreshApiTokenAndRetryIfExpired() {
        return errors -> errors.flatMap(error -> {
            if (error instanceof NetworkException && ((NetworkException) error).getResponse().getStatusCode() == 401) {
                // Re-try authenticating.
                //Timber.w("Attempting to refresh token");
                return Observable.fromCallable(() -> {
                    Credentials credentials = Credentials.userlessApp(redditAppClientId, Dank.sharedPrefs().getDeviceUuid());
                    redditAuthManager.refreshAccessToken(credentials);
                    return true;

                }).doOnNext(booleanObservable -> {
                    boolean isMainThread = Looper.getMainLooper() == Looper.myLooper();
                    Timber.i("isMainThread: %s", isMainThread);
                }).map(__ -> null);

            } else {
                return Observable.error(error);
            }
        });
    }

    public void logout() {
        redditClient.getOAuthHelper().revokeAccessToken(getLoginCredentials());
        redditClient.deauthenticate();
    }

    private Credentials getLoginCredentials() {
        if (loginCredentials == null) {
            loginCredentials = Credentials.installedApp(redditAppClientId, context.getString(R.string.reddit_app_redirect_url));
        }
        return loginCredentials;
    }

    public class UserLoginHelper {

        public UserLoginHelper() {
        }

        public String authorizationUrl() {
            String[] scopes = {
                    "account",
                    "edit",             // For editing comments and submissions
                    "history",
                    "identity",
                    "mysubreddits",
                    "privatemessages",
                    "read",
                    "report",           // For hiding or reporting a thread.
                    "save",
                    "submit",
                    "subscribe",
                    "vote",
                    "wikiread"
            };

            OAuthHelper oAuthHelper = redditClient.getOAuthHelper();
            return oAuthHelper.getAuthorizationUrl(getLoginCredentials(), true /* permanent */, true /* useMobileSite */, scopes).toString();
        }

        /**
         * Emits an item when the app is successfully able to authenticate the user in.
         */
        public Observable<Boolean> parseOAuthSuccessUrl(String successUrl) {
            return Observable.fromCallable(() -> {
                OAuthData oAuthData = redditClient.getOAuthHelper().onUserChallenge(successUrl, getLoginCredentials());
                redditClient.authenticate(oAuthData);
                return true;
            });
        }

    }

}

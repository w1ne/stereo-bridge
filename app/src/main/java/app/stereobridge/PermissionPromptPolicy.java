package app.stereobridge;
/** A denial is respected; expired one-time grants can request fresh access. */
final class PermissionPromptPolicy {
 private PermissionPromptPolicy() {}
 static boolean shouldRequest(boolean recordAction,boolean previouslyDenied,boolean rationale) {
  return !previouslyDenied || (recordAction && rationale);
 }
}

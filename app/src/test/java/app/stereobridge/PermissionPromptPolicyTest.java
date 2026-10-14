package app.stereobridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class PermissionPromptPolicyTest {
 @Test public void firstPreviewCanAsk() { assertTrue(PermissionPromptPolicy.shouldRequest(false,false,false)); }
 @Test public void deniedPreviewDoesNotAskAgain() { assertFalse(PermissionPromptPolicy.shouldRequest(false,true,true)); }
 @Test public void explicitRecordCanRetryRationale() { assertTrue(PermissionPromptPolicy.shouldRequest(true,true,true)); }
 @Test public void permanentDenialRequiresSettings() { assertFalse(PermissionPromptPolicy.shouldRequest(true,true,false)); }
 @Test public void expiredGrantedPermissionCanAskWithoutRationale() { assertTrue(PermissionPromptPolicy.shouldRequest(true,false,false)); }
}

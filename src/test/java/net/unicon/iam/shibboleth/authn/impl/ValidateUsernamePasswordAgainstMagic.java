package net.unicon.iam.shibboleth.authn.impl;

import net.shibboleth.idp.authn.AbstractValidationAction;
import net.shibboleth.idp.authn.AuthnEventIds;
import net.shibboleth.idp.authn.context.AuthenticationContext;
import net.shibboleth.idp.authn.context.UsernamePasswordContext;
import net.shibboleth.idp.authn.principal.UsernamePrincipal;
import org.opensaml.profile.context.ProfileRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.security.auth.Subject;

/**
 * This is just a simple validation action that makes sure that the username equals the password. Great for testing. Of
 * course, this is not thread safe, for reasons.
 */
public class ValidateUsernamePasswordAgainstMagic extends AbstractValidationAction {
    private static final Logger logger = LoggerFactory.getLogger(ValidateUsernamePasswordAgainstMagic.class);
    
    private UsernamePasswordContext usernamePasswordContext;

    @Override
    protected void doExecute(@Nonnull ProfileRequestContext profileRequestContext, @Nonnull AuthenticationContext authenticationContext) {
        this.usernamePasswordContext = authenticationContext.getSubcontext(UsernamePasswordContext.class);
        if (usernamePasswordContext == null) {
            logger.info(getLogPrefix() + "No UsernamePasswordContext available within authentication context");
            handleError(profileRequestContext, authenticationContext, "NoCredentials", AuthnEventIds.NO_CREDENTIALS);
            return;
        } else if (usernamePasswordContext.getUsername() == null || usernamePasswordContext.getUsername().equals("")) {
            logger.info(getLogPrefix() + "No username available within UsernamePasswordContext");
            handleError(profileRequestContext, authenticationContext, "NoCredentials", AuthnEventIds.NO_CREDENTIALS);
            return;
        } else if (usernamePasswordContext.getPassword() == null || usernamePasswordContext.getPassword().equals("")) {
            logger.info(getLogPrefix() + "No password available witin UsernamePasswordContext");
            handleError(profileRequestContext, authenticationContext, "InvalidCredentials", AuthnEventIds.INVALID_CREDENTIALS);
            return;
        } else if (!usernamePasswordContext.getUsername().equals(usernamePasswordContext.getPassword())) {
            logger.info(getLogPrefix() + "Login by " + usernamePasswordContext.getUsername() + " failed");
            handleError(profileRequestContext, authenticationContext, "InvalidCredentials", AuthnEventIds.INVALID_CREDENTIALS);
            return;
        } else {
            logger.info(getLogPrefix() + "Login by " + usernamePasswordContext.getUsername() + " succeeded");
            buildAuthenticationResult(profileRequestContext, authenticationContext);
        }
    }

    @Nonnull
    @Override
    protected Subject populateSubject(@Nonnull Subject subject) {
        subject.getPrincipals().add(new UsernamePrincipal(usernamePasswordContext.getUsername()));
        return subject;
    }
}

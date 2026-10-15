package at.fhtw.docflow.identity.api;

import at.fhtw.docflow.identity.application.CurrentOwner;
import at.fhtw.docflow.identity.application.OwnerDirectory;
import at.fhtw.docflow.identity.application.OwnerRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo")
public class PrincipalCurrentOwner implements CurrentOwner {
    private final HttpServletRequest request;
    private final OwnerDirectory owners;

    public PrincipalCurrentOwner(HttpServletRequest request, OwnerDirectory owners) {
        this.request = request;
        this.owners = owners;
    }

    @Override
    public long requireOwnerId() {
        var principal = request.getUserPrincipal();
        if (principal == null) {
            throw new OwnerRequiredException();
        }
        return owners.findIdByUsername(principal.getName()).orElseThrow(OwnerRequiredException::new);
    }
}

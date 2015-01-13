package org.fao.geonet.events.listeners;

import static org.fao.geonet.repository.specification.UserGroupSpecs.hasGroupId;
import static org.fao.geonet.repository.specification.UserGroupSpecs.hasProfile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.fao.geonet.domain.Group;
import org.fao.geonet.domain.Profile;
import org.fao.geonet.domain.User;
import org.fao.geonet.domain.UserGroup;
import org.fao.geonet.events.user.GroupLeft;
import org.fao.geonet.repository.UserGroupRepository;
import org.fao.geonet.utils.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Component;

/**
 * Warns PCAPI when a user has leaves a survey.
 * 
 * @author delawen
 * 
 *
 */
@Component
public class PCAPILeftGroup implements ApplicationListener<GroupLeft> {
    @Value("#{cobweb.PCAPI_URL}")
    private String PCAPI_URL;
    @Autowired
    private UserGroupRepository userGroupRepo;

    // TODO
    private String endpoint = "/";

    private org.fao.geonet.Logger log = Log.createLogger("cobweb");

    @Override
    public void onApplicationEvent(GroupLeft event) {
        User user = event.getUserGroup().getUser();
        Group group = event.getUserGroup().getGroup();
        CloseableHttpClient httpclient = HttpClients.createDefault();

        CloseableHttpResponse resp = null;
        try {
            URIBuilder builder = new URIBuilder(PCAPI_URL + endpoint);

            builder.setParameter("uuid", user.getUsername());
            builder.setParameter("action", "LEAVE");
            User coordinator = null;

            List<UserGroup> userGroups = userGroupRepo.findAll(Specifications
                    .where(hasGroupId(group.getId())).and(
                            hasProfile(Profile.UserAdmin)));

            if (userGroups.size() == 0) {
                log.error("Survey " + group.getName() + " corrupted!");
                log.error("Assign a coordinator as soon as possible!");
                log.error("User " + user.getUsername() + " can't join survey "
                        + group.getName());
                userGroupRepo.saveAndFlush(event.getUserGroup());
            } else if (userGroups.size() > 1) {
                log.error("Survey " + group.getName() + " corrupted!");
                log.error("Fixing survey " + group.getName()
                        + " by removing all coordinators except the first one");
                for (int i = 1; i < userGroups.size(); i++) {
                    userGroupRepo.delete(userGroups.get(i));
                }
                userGroupRepo.flush();
            } else {
                coordinator = userGroups.get(0).getUser();
            }

            builder.setParameter("survey", coordinator.getUsername());

            HttpGet httpGet = new HttpGet(builder.build());
            resp = httpclient.execute(httpGet);
            HttpEntity entity1 = resp.getEntity();
            EntityUtils.consume(entity1);

        } catch (IOException e) {
            log.error(e);
        } catch (URISyntaxException e) {
            log.error(e);
        } finally {
            try {
                if (resp != null) {
                    resp.close();
                }
            } catch (IOException e) {
                log.error(e);
            }
        }
    }
}
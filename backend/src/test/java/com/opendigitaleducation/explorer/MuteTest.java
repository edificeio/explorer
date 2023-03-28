package com.opendigitaleducation.explorer;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.explorer.to.TrashRequest;
import org.entcore.common.share.ShareRoles;
import org.entcore.common.user.UserInfos;
import org.entcore.test.HttpTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.opendigitaleducation.explorer.tests.ExplorerTestHelper.executeJobAndFetchUniqueResult;
import static com.opendigitaleducation.explorer.tests.ExplorerTestHelper.executeJobNTimes;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.*;

/**
 * Test muting of resources for a particular user (c.f. <a href="https://opendigitaleducation.atlassian.net/browse/WB2-41">JIRA ticket</a>).
 */
@RunWith(VertxUnitRunner.class)
public class MuteTest extends FullExplorerStackTest {

    /**
     * <p>
     *   <u>GOAL</u> : Test that a user with read permissions can mute the notifications by putting it to its bin.
     *</p>
     * <p>
     * <u>STEPS</u> :
     *  <ol>
     *      <li>Create 3 users A, B and C</li>
     *      <li>A creates a resource R</li>
     *      <li>A shares the resource to B and C</li>
     *      <li>EUR ingests R and its shares</li>
     *      <li>Check that R is not muted for both B and C</li>
     *      <li>B trashes the resource</li>
     *      <li>Check that R is muted for B but not for C</li>
     *      <li>C trashes the resource</li>
     *      <li>Check that R is muted for both B and C</li>
     *      <li>B restores the resource</li>
     *      <li>Check that R is muted for C but not for B</li>
     *      <li>C restores the resource</li>
     *      <li>Check that R is not muted for both B and C</li>
     * </ol>
     * </p>
     * @param context Test context
     */
    @Test
    public void testReaderCanMuteAResourceThatHasBeenSharedDirectly(final TestContext context) {
        final String resourceName = "muted_resource _" + currentTimeMillis();
        final String id = "muted_resource_" + currentTimeMillis();
        final UserInfos creator = test.directory().generateUser("creator", "creator");
        final UserInfos reader1 = test.directory().generateUser("reader1", "reader1");
        final UserInfos reader2 = test.directory().generateUser("reader2", "reader2");
        final JsonObject mutedResource = resource(resourceName)
                .put("creatorId", creator.getUserId())
                .put("_id", id);
        final Set<String> userIds = new HashSet<>();
        userIds.add(reader1.getUserId());
        userIds.add(reader2.getUserId());
        final JsonObject shares = createShareForUsers(userIds, singletonList(ShareRoles.Read.key));
        plugin.start();
        createUsersAndTheirGroups(creator, reader1, reader2)
        .compose(e -> plugin.create(creator, mutedResource, false))
        .compose(e -> executeJobAndFetchUniqueResult(job, application, resourceService, creator, resourceName, context))
        .compose(createdResource ->
            pluginClient.shareByIds(creator, singleton(createdResource.getString("assetId")), shares)
            .compose(e -> executeJobAndFetchUniqueResult(job, application, resourceService, creator, resourceName, context))
            .map(e -> createdResource)
        )
        .compose(createdResource -> {
            final String resourceId = createdResource.getString("_id");
            final String entId = createdResource.getString("assetId");
            return muteHelper.fetResourceMutesByEntId(resourceId).onComplete(context.asyncAssertSuccess(originalMuters -> {
                context.assertTrue(originalMuters.isEmpty(), "The resource should have no muters after its creation");
                muteResource(resourceId, reader1, context).compose(e -> {
                    // Check that mute status was applied to first reader only
                    return assertMuteStatus(entId, setOf(reader1), setOf(reader2, creator), context);
                })
                .compose(trashResponse -> muteResource(resourceId, reader2, context))
                .compose(e -> {
                    // Check that mute status was applied to second and that first reader still have their status set
                    return assertMuteStatus(entId, setOf(reader1, reader2), setOf(creator), context);
                })
                .compose(trashResponse -> restoreResource(resourceId, reader1, context))
                .compose(e -> {
                    // Check that reader now has notifications
                    return assertMuteStatus(entId, setOf(reader2), setOf(reader1, creator), context);
                })
                .compose(trashResponse -> restoreResource(resourceId, reader2, context))
                .compose(e -> {
                    // Check that now no one has muted the resource
                    return assertMuteStatus(entId, setOf(), setOf(reader1, reader2, creator), context);
                }).onComplete(context.asyncAssertSuccess())
                .onFailure(context::fail);
            }));
        })
        .onComplete(context.asyncAssertSuccess());
    }

    private Future<?> createUsersAndTheirGroups(UserInfos... users) {
        return CompositeFuture.all(Arrays.stream(users).map(user -> test.directory().createActiveUser(user)).collect(Collectors.toList()))
            .compose(e ->
                    CompositeFuture.all(Arrays.stream(users).map(user -> test.directory().createGroup(user.getUserId(), user.getUserId())).collect(Collectors.toList())))
            .compose(e ->
                    CompositeFuture.all(Arrays.stream(users).map(user -> test.directory().attachUserToGroup(user.getUserId(), user.getUserId())).collect(Collectors.toList()))
            );
    }

    private Set<String> setOf(final UserInfos... users) {
        return Arrays.stream(users).map(UserInfos::getUserId).collect(Collectors.toSet());
    }

    private Future<Object> muteResource(String resourceId, UserInfos muter, TestContext context) {
        return trashOrRestoreResource(resourceId, muter, context, true);
    }

    private Future<Object> restoreResource(String resourceId, UserInfos muter, TestContext context) {
        return trashOrRestoreResource(resourceId, muter, context, false);
    }

    private Future<Object> trashOrRestoreResource(String resourceId, UserInfos muter, TestContext context, final boolean trash) {
        final TrashRequest trashRequest = new TrashRequest(plugin.getApplication(), singleton(resourceId), emptySet());
        final HttpTestHelper.TestHttpServerRequest trashHttpRequest = test.http()
                .put(trash ? "/trash" : "/restore", new JsonObject(), JsonObject.mapFrom(trashRequest));
        final Promise<JsonObject> promiseTrashDone = Promise.promise();
        trashHttpRequest.response().endJsonHandler(promiseTrashDone::complete);
        try {
            if(trash) {
                controller.trashBatch(trashHttpRequest.withSession(muter));
            } else {
                controller.restoreBatch(trashHttpRequest.withSession(muter));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Check that mute status was applied to first reader
        return promiseTrashDone.future()
                .compose(e -> {
                    final Promise<Object> p = Promise.promise();
                    test.vertx().setTimer(2000L, ee -> p.complete(e));
                    return p.future();
                })
                .compose(e -> executeJobNTimes(job, 1, context));
    }

    private Future<Set<String>> assertMuteStatus(String entId, Set<String> muters, Set<String> nonMuters, TestContext context) {
        return muteHelper.fetResourceMutesByEntId(entId).map(registeredMuters -> {
            for (String muter : muters) {
                context.assertTrue(registeredMuters.contains(muter), muter + " has muted the resource but explorer doesn't see it");
            }
            for (String nonMuter : nonMuters) {
                context.assertFalse(registeredMuters.contains(nonMuter), nonMuter + " has not muted or unmuted the resource but explorer doesn't see it");
            }
           return registeredMuters;
        }).onFailure(context::fail);
    }

    private JsonObject createShareForUsers(final Set<String> users, final List<String> actions) {
        final JsonObject userShares = new JsonObject();
        for (String user : users) {
            userShares.put(user, new JsonArray(actions));
        }
        return new JsonObject().put("users", userShares);
    }
}

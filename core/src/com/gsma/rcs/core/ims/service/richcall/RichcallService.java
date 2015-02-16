/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * NOTE: This file has been modified by Sony Mobile Communications Inc.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.gsma.rcs.core.ims.service.richcall;

import static com.gsma.rcs.utils.StringUtils.UTF8;

import com.gsma.rcs.core.CoreException;
import com.gsma.rcs.core.content.ContentManager;
import com.gsma.rcs.core.content.GeolocContent;
import com.gsma.rcs.core.content.MmContent;
import com.gsma.rcs.core.content.VideoContent;
import com.gsma.rcs.core.ims.ImsModule;
import com.gsma.rcs.core.ims.network.sip.FeatureTags;
import com.gsma.rcs.core.ims.network.sip.SipUtils;
import com.gsma.rcs.core.ims.protocol.sip.SipRequest;
import com.gsma.rcs.core.ims.service.ImsService;
import com.gsma.rcs.core.ims.service.ImsServiceSession;
import com.gsma.rcs.core.ims.service.richcall.geoloc.GeolocTransferSession;
import com.gsma.rcs.core.ims.service.richcall.geoloc.OriginatingGeolocTransferSession;
import com.gsma.rcs.core.ims.service.richcall.geoloc.TerminatingGeolocTransferSession;
import com.gsma.rcs.core.ims.service.richcall.image.ImageTransferSession;
import com.gsma.rcs.core.ims.service.richcall.image.OriginatingImageTransferSession;
import com.gsma.rcs.core.ims.service.richcall.image.TerminatingImageTransferSession;
import com.gsma.rcs.core.ims.service.richcall.video.OriginatingVideoStreamingSession;
import com.gsma.rcs.core.ims.service.richcall.video.TerminatingVideoStreamingSession;
import com.gsma.rcs.core.ims.service.richcall.video.VideoStreamingSession;
import com.gsma.rcs.provider.eab.ContactsManager;
import com.gsma.rcs.provider.settings.RcsSettings;
import com.gsma.rcs.utils.ContactUtils;
import com.gsma.rcs.utils.logger.Logger;
import com.gsma.services.rcs.Geoloc;
import com.gsma.services.rcs.RcsContactFormatException;
import com.gsma.services.rcs.contacts.ContactId;
import com.gsma.services.rcs.sharing.geoloc.GeolocSharing;
import com.gsma.services.rcs.sharing.image.ImageSharing;
import com.gsma.services.rcs.sharing.video.VideoSharing;
import com.gsma.services.rcs.sharing.video.IVideoPlayer;

import java.util.HashMap;
import java.util.Map;

import javax2.sip.message.Response;

/**
 * Rich call service has in charge to monitor the GSM call in order to stop the current content
 * sharing when the call terminates, to process capability request from remote and to request remote
 * capabilities.
 * 
 * @author Jean-Marc AUFFRET
 */
public class RichcallService extends ImsService {
    /**
     * Video share features tags
     */
    public final static String[] FEATURE_TAGS_VIDEO_SHARE = {
            FeatureTags.FEATURE_3GPP_VIDEO_SHARE
    };

    /**
     * Image share features tags
     */
    public final static String[] FEATURE_TAGS_IMAGE_SHARE = {
            FeatureTags.FEATURE_3GPP_VIDEO_SHARE,
            FeatureTags.FEATURE_3GPP_IMAGE_SHARE
    };

    /**
     * Geoloc share features tags
     */
    public final static String[] FEATURE_TAGS_GEOLOC_SHARE = {
            FeatureTags.FEATURE_3GPP_VIDEO_SHARE, FeatureTags.FEATURE_3GPP_LOCATION_SHARE
    };

    /**
     * The logger
     */
    private final static Logger sLogger = Logger.getLogger(RichcallService.class.getSimpleName());

    /**
     * ImageTransferSessionCache with Session ID as key
     */
    private Map<String, ImageTransferSession> mImageTransferSessionCache = new HashMap<String, ImageTransferSession>();

    /**
     * VideoStreamingSessionCache with Session ID as key
     */
    private Map<String, VideoStreamingSession> mVideoStremaingSessionCache = new HashMap<String, VideoStreamingSession>();

    /**
     * GeolocTransferSessionCache with Session ID as key
     */
    private Map<String, GeolocTransferSession> mGeolocTransferSessionCache = new HashMap<String, GeolocTransferSession>();

    /**
     * Contacts manager
     */
    private final ContactsManager mContactsManager;

    private final RcsSettings mRcsSettings;

    /**
     * Constructor
     * 
     * @param parent IMS module
     * @param contactsManager ContactsManager
     * @param rcsSettings
     * @throws CoreException
     */
    public RichcallService(ImsModule parent, ContactsManager contactsManager,
            RcsSettings rcsSettings) throws CoreException {
        super(parent, true);

        mContactsManager = contactsManager;
        mRcsSettings = rcsSettings;
    }

    private void handleImageSharingInvitationRejected(SipRequest invite, int reasonCode) {
        ContactId contact = ContactUtils.createContactId(SipUtils.getAssertedIdentity(invite));
        MmContent content = ContentManager.createMmContentFromSdp(invite);
        getImsModule().getCore().getListener()
                .handleImageSharingInvitationRejected(contact, content, reasonCode);
    }

    private void handleVideoSharingInvitationRejected(SipRequest invite, int reasonCode) {
        ContactId contact = ContactUtils.createContactId(SipUtils.getAssertedIdentity(invite));
        VideoContent content = ContentManager.createLiveVideoContentFromSdp(invite.getSdpContent()
                .getBytes(UTF8));
        getImsModule().getCore().getListener()
                .handleVideoSharingInvitationRejected(contact, content, reasonCode);
    }

    private void handleGeolocSharingInvitationRejected(SipRequest invite, int reasonCode) {
        ContactId contact = ContactUtils.createContactId(SipUtils.getAssertedIdentity(invite));
        GeolocContent content = (GeolocContent) ContentManager.createMmContentFromSdp(invite);
        getImsModule().getCore().getListener()
                .handleGeolocSharingInvitationRejected(contact, content, reasonCode);
    }

    /**
     * Start the IMS service
     */
    public synchronized void start() {
        if (isServiceStarted()) {
            // Already started
            return;
        }
        setServiceStarted(true);
    }

    /**
     * Stop the IMS service
     */
    public synchronized void stop() {
        if (!isServiceStarted()) {
            // Already stopped
            return;
        }
        setServiceStarted(false);
    }

    /**
     * Check the IMS service
     */
    public void check() {
    }

    private ImageTransferSession getUnidirectionalImageSharingSession() {
        if (sLogger.isActivated()) {
            sLogger.debug("Get Unidirection ImageTransferSession ");
        }
        synchronized (getImsServiceSessionOperationLock()) {
            return mImageTransferSessionCache.values().iterator().next();
        }
    }

    private boolean isCurrentlyImageSharingUniDirectional() {
        synchronized (getImsServiceSessionOperationLock()) {
            return mImageTransferSessionCache.size() >= SharingDirection.UNIDIRECTIONAL;
        }
    }

    private boolean isCurrentlyImageSharingBiDirectional() {
        synchronized (getImsServiceSessionOperationLock()) {
            return mImageTransferSessionCache.size() >= SharingDirection.BIDIRECTIONAL;
        }
    }

    private void assertMaximumImageTransferSize(long size, String errorMessage)
            throws CoreException {
        long maxSize = ImageTransferSession.getMaxImageSharingSize();
        if (maxSize > 0 && size > maxSize) {
            if (sLogger.isActivated()) {
                sLogger.error(errorMessage);
            }
            /*
             * TODO : Proper exception handling will be added here as part of the CR037
             * implementation
             */
            throw new CoreException(errorMessage);
        }
    }

    private VideoStreamingSession getUnidirectionalVideoSharingSession() {
        if (sLogger.isActivated()) {
            sLogger.debug("Get Unidirection VideoStreamingSession ");
        }
        synchronized (getImsServiceSessionOperationLock()) {
            return mVideoStremaingSessionCache.values().iterator().next();
        }
    }

    private boolean isCurrentlyVideoSharingUniDirectional() {
        synchronized (getImsServiceSessionOperationLock()) {
            return mVideoStremaingSessionCache.size() >= SharingDirection.UNIDIRECTIONAL;
        }
    }

    private boolean isCurrentlyVideoSharingBiDirectional() {
        synchronized (getImsServiceSessionOperationLock()) {
            return mVideoStremaingSessionCache.size() >= SharingDirection.BIDIRECTIONAL;
        }
    }

    private GeolocTransferSession getUnidirectionalGeolocSharingSession() {
        if (sLogger.isActivated()) {
            sLogger.debug("Get Unidirection GeolocTransferSession ");
        }
        synchronized (getImsServiceSessionOperationLock()) {
            return mGeolocTransferSessionCache.values().iterator().next();
        }
    }

    private boolean isCurrentlyGeolocSharingUniDirectional() {
        synchronized (getImsServiceSessionOperationLock()) {
            return mGeolocTransferSessionCache.size() >= SharingDirection.UNIDIRECTIONAL;
        }
    }

    private boolean isCurrentlyGeolocSharingBiDirectional() {
        synchronized (getImsServiceSessionOperationLock()) {
            return mGeolocTransferSessionCache.size() >= SharingDirection.BIDIRECTIONAL;
        }
    }

    public void addSession(ImageTransferSession session) {
        String sessionId = session.getSessionID();
        if (sLogger.isActivated()) {
            sLogger.debug(new StringBuilder("Add ImageTransferSession with sessionId '")
                    .append(sessionId).append("'").toString());
        }
        synchronized (getImsServiceSessionOperationLock()) {
            mImageTransferSessionCache.put(sessionId, session);
            addImsServiceSession(session);
        }
    }

    public void removeSession(final ImageTransferSession session) {
        final String sessionId = session.getSessionID();
        if (sLogger.isActivated()) {
            sLogger.debug(new StringBuilder("Remove ImageTransferSession with sessionId '")
                    .append(sessionId).append("'").toString());
        }
        /*
         * Performing remove session operation on a new thread so that ongoing threads accessing
         * that session can finish up before it is actually removed
         */
        new Thread() {
            @Override
            public void run() {
                synchronized (getImsServiceSessionOperationLock()) {
                    mImageTransferSessionCache.remove(sessionId);
                    removeImsServiceSession(session);
                }
            }
        }.start();
    }

    public ImageTransferSession getImageTransferSession(String sessionId) {
        if (sLogger.isActivated()) {
            sLogger.debug(new StringBuilder("Get ImageTransferSession with sessionId '")
                    .append(sessionId).append("'").toString());
        }
        synchronized (getImsServiceSessionOperationLock()) {
            return mImageTransferSessionCache.get(sessionId);
        }
    }

    public void addSession(VideoStreamingSession session) {
        String sessionId = session.getSessionID();
        if (sLogger.isActivated()) {
            sLogger.debug(new StringBuilder("Add VideoStreamingSession with sessionId '")
                    .append(sessionId).append("'").toString());
        }
        synchronized (getImsServiceSessionOperationLock()) {
            mVideoStremaingSessionCache.put(sessionId, session);
            addImsServiceSession(session);
        }
    }

    public void removeSession(final VideoStreamingSession session) {
        final String sessionId = session.getSessionID();
        if (sLogger.isActivated()) {
            sLogger.debug(new StringBuilder("Remove VideoStreamingSession with sessionId '")
                    .append(sessionId).append("'").toString());
        }
        /*
         * Performing remove session operation on a new thread so that ongoing threads accessing
         * that session can finish up before it is actually removed
         */
        new Thread() {
            @Override
            public void run() {
                synchronized (getImsServiceSessionOperationLock()) {
                    mVideoStremaingSessionCache.remove(sessionId);
                    removeImsServiceSession(session);
                }
            }
        }.start();
    }

    public VideoStreamingSession getVideoSharingSession(String sessionId) {
        if (sLogger.isActivated()) {
            sLogger.debug(new StringBuilder("Get VideoStreamingSession with sessionId '")
                    .append(sessionId).append("'").toString());
        }
        synchronized (getImsServiceSessionOperationLock()) {
            return mVideoStremaingSessionCache.get(sessionId);
        }
    }

    public void addSession(GeolocTransferSession session) {
        String sessionId = session.getSessionID();
        if (sLogger.isActivated()) {
            sLogger.debug(new StringBuilder("Add GeolocTransferSession with sessionId '")
                    .append(sessionId).append("'").toString());
        }
        synchronized (getImsServiceSessionOperationLock()) {
            mGeolocTransferSessionCache.put(sessionId, session);
            addImsServiceSession(session);
        }
    }

    public void removeSession(final GeolocTransferSession session) {
        final String sessionId = session.getSessionID();
        if (sLogger.isActivated()) {
            sLogger.debug(new StringBuilder("Remove GeolocTransferSession with sessionId '")
                    .append(sessionId).append("'").toString());
        }
        /*
         * Performing remove session operation on a new thread so that ongoing threads accessing
         * that session can finish up before it is actually removed
         */
        new Thread() {
            @Override
            public void run() {
                synchronized (getImsServiceSessionOperationLock()) {
                    mGeolocTransferSessionCache.remove(sessionId);
                    removeImsServiceSession(session);
                }
            }
        }.start();
    }

    public GeolocTransferSession getGeolocTransferSession(String sessionId) {
        if (sLogger.isActivated()) {
            sLogger.debug(new StringBuilder("Get GeolocTransferSession with sessionId '")
                    .append(sessionId).append("'").toString());
        }
        synchronized (getImsServiceSessionOperationLock()) {
            return mGeolocTransferSessionCache.get(sessionId);
        }
    }

    /**
     * Is call connected with a given contact
     * 
     * @param contact Contact Id
     * @return Boolean
     */
    public boolean isCallConnectedWith(ContactId contact) {
        boolean csCall = (getImsModule() != null) && (getImsModule().getCallManager() != null)
                && getImsModule().getCallManager().isCallConnectedWith(contact);
        boolean ipCall = (getImsModule() != null) && (getImsModule().getIPCallService() != null)
                && getImsModule().getIPCallService().isCallConnectedWith(contact);
        return (csCall || ipCall);
    }

    /**
     * Initiate an image sharing session
     * 
     * @param contact Remote contact identifier
     * @param content The file content to share
     * @param thumbnail The thumbnail content
     * @return CSh session
     * @throws CoreException
     */
    public ImageTransferSession initiateImageSharingSession(ContactId contact, MmContent content,
            MmContent thumbnail) throws CoreException {
        if (sLogger.isActivated()) {
            sLogger.info("Initiate image sharing session with contact " + contact + ", file "
                    + content.toString());
        }

        // Test if call is established
        if (!isCallConnectedWith(contact)) {
            if (sLogger.isActivated()) {
                sLogger.debug("Rich call not established: cancel the initiation");
            }
            /*
             * TODO : Proper exception handling will be added here as part of the CR037
             * implementation
             */
            throw new CoreException("Call not established");
        }

        assertMaximumImageTransferSize(content.getSize(), "File exceeds max size.");

        // Reject if there are already 2 bidirectional sessions with a given contact
        boolean rejectInvitation = false;
        if (isCurrentlyImageSharingBiDirectional()) {
            // Already a bidirectional session
            if (sLogger.isActivated()) {
                sLogger.debug("Max sessions reached");
            }
            rejectInvitation = true;
        } else if (isCurrentlyImageSharingUniDirectional()) {
            ImageTransferSession currentSession = getUnidirectionalImageSharingSession();
            if (isSessionOriginating(currentSession)) {
                // Originating session already used
                if (sLogger.isActivated()) {
                    sLogger.debug("Max originating sessions reached");
                }
                rejectInvitation = true;
            } else if (contact == null || !contact.equals(currentSession.getRemoteContact())) {
                // Not the same contact
                if (sLogger.isActivated()) {
                    sLogger.debug("Only bidirectional session with same contact authorized");
                }
                rejectInvitation = true;
            }
        }
        if (rejectInvitation) {
            if (sLogger.isActivated()) {
                sLogger.debug("The max number of sharing sessions is achieved: cancel the initiation");
            }
            throw new CoreException("Max content sharing sessions achieved");
        }

        // Create a new session
        OriginatingImageTransferSession session = new OriginatingImageTransferSession(this,
                content, contact, thumbnail, mRcsSettings);

        return session;
    }

    /**
     * Receive an image sharing invitation
     * 
     * @param invite Initial invite
     */
    public void receiveImageSharingInvitation(SipRequest invite) {
        if (sLogger.isActivated()) {
            sLogger.info("Receive an image sharing session invitation");
        }

        // Parse contact
        ContactId contact = null;
        try {
            contact = ContactUtils.createContactId(SipUtils.getAssertedIdentity(invite));
        } catch (RcsContactFormatException e) {
            if (sLogger.isActivated()) {
                sLogger.debug("Rich call not established: cannot parse contact");
            }
            return;
        }

        // Test if call is established
        if (!isCallConnectedWith(contact)) {
            if (sLogger.isActivated()) {
                sLogger.debug("Rich call not established: reject the invitation");
            }
            sendErrorResponse(invite, 606);
            return;
        }

        // Test if the contact is blocked
        if (mContactsManager.isBlockedForContact(contact)) {
            if (sLogger.isActivated()) {
                sLogger.debug("Contact " + contact
                        + " is blocked: automatically reject the sharing invitation");
            }

            // Send a 603 Decline response
            sendErrorResponse(invite, Response.DECLINE);
            return;
        }

        // Reject if there are already 2 bidirectional sessions with a given contact
        boolean rejectInvitation = false;
        if (isCurrentlyImageSharingBiDirectional()) {
            // Already a bidirectional session
            if (sLogger.isActivated()) {
                sLogger.debug("Max sessions reached");
            }
            rejectInvitation = true;
            handleImageSharingInvitationRejected(invite,
                    ImageSharing.ReasonCode.REJECTED_MAX_SHARING_SESSIONS);
        } else if (isCurrentlyImageSharingUniDirectional()) {
            ImageTransferSession currentSession = getUnidirectionalImageSharingSession();
            if (isSessionTerminating(currentSession)) {
                // Terminating session already used
                if (sLogger.isActivated()) {
                    sLogger.debug("Max terminating sessions reached");
                }
                rejectInvitation = true;
            } else if (contact == null || !contact.equals(currentSession.getRemoteContact())) {
                // Not the same contact
                if (sLogger.isActivated()) {
                    sLogger.debug("Only bidirectional session with same contact authorized");
                }
                rejectInvitation = true;
                handleImageSharingInvitationRejected(invite,
                        ImageSharing.ReasonCode.REJECTED_MAX_SHARING_SESSIONS);
            }
        }
        if (rejectInvitation) {
            if (sLogger.isActivated()) {
                sLogger.debug("Reject the invitation");
            }
            sendErrorResponse(invite, 486);
            return;
        }

        // Create a new session
        ImageTransferSession session = new TerminatingImageTransferSession(this, invite, contact,
                mRcsSettings);

        getImsModule().getCore().getListener().handleContentSharingTransferInvitation(session);

        session.startSession();
    }

    /**
     * Initiate a live video sharing session
     * 
     * @param contact Remote contact Id
     * @param content Video content to share
     * @param player Media player
     * @return CSh session
     * @throws CoreException
     */
    public VideoStreamingSession initiateLiveVideoSharingSession(ContactId contact,
            IVideoPlayer player) throws CoreException {
        if (sLogger.isActivated()) {
            sLogger.info("Initiate a live video sharing session");
        }

        // Test if call is established
        if (!isCallConnectedWith(contact)) {
            if (sLogger.isActivated()) {
                sLogger.debug("Rich call not established: cancel the initiation");
            }
            /*
             * TODO : Proper exception handling will be added here as part of the CR037
             * implementation
             */
            throw new CoreException("Call not established");
        }

        // Reject if there are already 2 bidirectional sessions with a given contact
        boolean rejectInvitation = false;
        if (isCurrentlyVideoSharingBiDirectional()) {
            // Already a bidirectional session
            if (sLogger.isActivated()) {
                sLogger.debug("Max sessions reached");
            }
            rejectInvitation = true;
        } else if (isCurrentlyVideoSharingUniDirectional()) {
            VideoStreamingSession currentSession = getUnidirectionalVideoSharingSession();
            if (isSessionOriginating(currentSession)) {
                // Originating session already used
                if (sLogger.isActivated()) {
                    sLogger.debug("Max originating sessions reached");
                }
                rejectInvitation = true;
            } else if (contact == null || !contact.equals(currentSession.getRemoteContact())) {
                // Not the same contact
                if (sLogger.isActivated()) {
                    sLogger.debug("Only bidirectional session with same contact authorized");
                }
                rejectInvitation = true;
            }
        }
        if (rejectInvitation) {
            if (sLogger.isActivated()) {
                sLogger.debug("The max number of sharing sessions is achieved: cancel the initiation");
            }
            /*
             * TODO : Proper exception handling will be added here as part of the CR037
             * implementation
             */
            throw new CoreException("Max content sharing sessions achieved");
        }

        // Create a new session
        OriginatingVideoStreamingSession session = new OriginatingVideoStreamingSession(this,
                player, ContentManager.createGenericLiveVideoContent(), contact, mRcsSettings);

        return session;
    }

    /**
     * Receive a video sharing invitation
     * 
     * @param invite Initial invite
     */
    public void receiveVideoSharingInvitation(SipRequest invite) {
        if (sLogger.isActivated()) {
            sLogger.info("Receive a video sharing invitation");
        }

        // Parse contact
        ContactId contact = null;
        try {
            contact = ContactUtils.createContactId(SipUtils.getAssertedIdentity(invite));
        } catch (RcsContactFormatException e) {
            if (sLogger.isActivated()) {
                sLogger.debug("Rich call not established: cannot parse contact");
            }
            return;
        }

        // Test if call is established
        if (!isCallConnectedWith(contact)) {
            if (sLogger.isActivated()) {
                sLogger.debug("Rich call not established: reject the invitation");
            }
            sendErrorResponse(invite, 606);
            return;
        }

        // Test if the contact is blocked
        if (mContactsManager.isBlockedForContact(contact)) {
            if (sLogger.isActivated()) {
                sLogger.debug("Contact " + contact
                        + " is blocked: automatically reject the sharing invitation");
            }

            // Send a 603 Decline response
            sendErrorResponse(invite, Response.DECLINE);
            return;
        }

        // Reject if there are already 2 bidirectional sessions with a given contact
        boolean rejectInvitation = false;
        if (isCurrentlyVideoSharingBiDirectional()) {
            // Already a bidirectional session
            if (sLogger.isActivated()) {
                sLogger.debug("Max sessions reached");
            }
            rejectInvitation = true;
            handleVideoSharingInvitationRejected(invite,
                    VideoSharing.ReasonCode.REJECTED_MAX_SHARING_SESSIONS);
        } else if (isCurrentlyVideoSharingUniDirectional()) {
            VideoStreamingSession currentSession = getUnidirectionalVideoSharingSession();
            if (isSessionTerminating(currentSession)) {
                // Terminating session already used
                if (sLogger.isActivated()) {
                    sLogger.debug("Max terminating sessions reached");
                }
                rejectInvitation = true;
                handleVideoSharingInvitationRejected(invite,
                        VideoSharing.ReasonCode.REJECTED_MAX_SHARING_SESSIONS);
            } else if (contact == null || !contact.equals(currentSession.getRemoteContact())) {
                // Not the same contact
                if (sLogger.isActivated()) {
                    sLogger.debug("Only bidirectional session with same contact authorized");
                }
                rejectInvitation = true;
                handleVideoSharingInvitationRejected(invite,
                        VideoSharing.ReasonCode.REJECTED_MAX_SHARING_SESSIONS);
            }
        }
        if (rejectInvitation) {
            if (sLogger.isActivated()) {
                sLogger.debug("Reject the invitation");
            }
            sendErrorResponse(invite, 486);
            return;
        }

        // Create a new session
        VideoStreamingSession session = new TerminatingVideoStreamingSession(this, invite, contact,
                mRcsSettings);

        getImsModule().getCore().getListener().handleContentSharingStreamingInvitation(session);

        session.startSession();
    }

    /**
     * Initiate a geoloc sharing session
     * 
     * @param contact Remote contact
     * @param content Content to be shared
     * @param geoloc Geolocation
     * @return GeolocTransferSession
     * @throws CoreException
     */
    public GeolocTransferSession initiateGeolocSharingSession(ContactId contact, MmContent content,
            Geoloc geoloc) throws CoreException {
        if (sLogger.isActivated()) {
            sLogger.info(new StringBuilder("Initiate geoloc sharing session with contact ")
                    .append(contact).append(".").toString());
        }
        if (!isCallConnectedWith(contact)) {
            if (sLogger.isActivated()) {
                sLogger.debug("Rich call not established: cancel the initiation.");
            }
            /*
             * TODO : Proper exception handling will be added here as part of the CR037
             * implementation
             */
            throw new CoreException("Call not established");
        }

        // Create a new session
        OriginatingGeolocTransferSession session = new OriginatingGeolocTransferSession(this,
                content, contact, geoloc, mRcsSettings);

        return session;
    }

    /**
     * Receive a geoloc sharing invitation
     * 
     * @param invite Initial invite
     */
    public void receiveGeolocSharingInvitation(SipRequest invite) {
        if (sLogger.isActivated()) {
            sLogger.info("Receive a geoloc sharing session invitation");
        }

        // Parse contact
        ContactId contact = null;
        try {
            contact = ContactUtils.createContactId(SipUtils.getAssertedIdentity(invite));
        } catch (RcsContactFormatException e) {
            if (sLogger.isActivated()) {
                sLogger.debug("Rich call not established: cannot parse contact");
            }
            return;
        }

        // Test if call is established
        if (!isCallConnectedWith(contact)) {
            if (sLogger.isActivated()) {
                sLogger.debug("Rich call not established: reject the invitation");
            }
            sendErrorResponse(invite, 606);
            return;
        }

        // Test if the contact is blocked
        if (mContactsManager.isBlockedForContact(contact)) {
            if (sLogger.isActivated()) {
                sLogger.debug("Contact " + contact
                        + " is blocked: automatically reject the sharing invitation");
            }

            // Send a 603 Decline response
            sendErrorResponse(invite, Response.DECLINE);
            return;
        }

        // Reject if there are already 2 bidirectional sessions with a given contact
        boolean rejectInvitation = false;
        if (isCurrentlyGeolocSharingBiDirectional()) {
            // Already a bidirectional session
            if (sLogger.isActivated()) {
                sLogger.debug("Max sessions reached");
            }
            handleGeolocSharingInvitationRejected(invite,
                    GeolocSharing.ReasonCode.REJECTED_MAX_SHARING_SESSIONS);
            rejectInvitation = true;
        } else if (isCurrentlyGeolocSharingUniDirectional()) {
            GeolocTransferSession currentSession = getUnidirectionalGeolocSharingSession();
            if (isSessionTerminating(currentSession)) {
                // Terminating session already used
                if (sLogger.isActivated()) {
                    sLogger.debug("Max terminating sessions reached");
                }
                handleGeolocSharingInvitationRejected(invite,
                        GeolocSharing.ReasonCode.REJECTED_MAX_SHARING_SESSIONS);
                rejectInvitation = true;
            } else if (contact == null || !contact.equals(currentSession.getRemoteContact())) {
                // Not the same contact
                if (sLogger.isActivated()) {
                    sLogger.debug("Only bidirectional session with same contact authorized");
                }
                handleGeolocSharingInvitationRejected(invite,
                        GeolocSharing.ReasonCode.REJECTED_MAX_SHARING_SESSIONS);
                rejectInvitation = true;
            }
        }
        if (rejectInvitation) {
            if (sLogger.isActivated()) {
                sLogger.debug("Reject the invitation");
            }
            sendErrorResponse(invite, 486);
            return;
        }

        // Create a new session
        GeolocTransferSession session = new TerminatingGeolocTransferSession(this, invite, contact,
                mRcsSettings);

        getImsModule().getCore().getListener().handleContentSharingTransferInvitation(session);

        session.startSession();
    }

    /**
     * Abort all pending sessions
     */
    public void abortAllSessions() {
        if (sLogger.isActivated()) {
            sLogger.debug("Abort all pending sessions");
        }
        abortAllSessions(ImsServiceSession.TERMINATION_BY_SYSTEM);
    }

    /**
     * Is the current session an originating one
     * 
     * @param session
     * @return true if session is an originating content sharing session (image or video)
     */
    private boolean isSessionOriginating(ContentSharingSession session) {
        return (session instanceof OriginatingImageTransferSession || session instanceof OriginatingVideoStreamingSession);
    }

    /**
     * Is the current session a terminating one
     * 
     * @param session
     * @return true if session is an terminating content sharing session (image or video)
     */
    private boolean isSessionTerminating(ContentSharingSession session) {
        return (session instanceof TerminatingImageTransferSession || session instanceof TerminatingVideoStreamingSession);
    }
}
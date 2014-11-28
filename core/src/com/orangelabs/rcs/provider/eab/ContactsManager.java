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

package com.orangelabs.rcs.provider.eab;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.accounts.AccountManager;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.StatusUpdates;

import com.gsma.services.rcs.RcsContactFormatException;
import com.gsma.services.rcs.capability.CapabilitiesLog;
import com.gsma.services.rcs.contacts.ContactId;
import com.gsma.services.rcs.contacts.ContactsProvider;
import com.orangelabs.rcs.R;
import com.orangelabs.rcs.addressbook.AuthenticationService;
import com.orangelabs.rcs.core.ims.service.ContactInfo;
import com.orangelabs.rcs.core.ims.service.ContactInfo.RcsStatus;
import com.orangelabs.rcs.core.ims.service.ContactInfo.RegistrationState;
import com.orangelabs.rcs.core.ims.service.capability.Capabilities;
import com.orangelabs.rcs.core.ims.service.extension.ServiceExtensionManager;
import com.orangelabs.rcs.core.ims.service.presence.FavoriteLink;
import com.orangelabs.rcs.core.ims.service.presence.Geoloc;
import com.orangelabs.rcs.core.ims.service.presence.PhotoIcon;
import com.orangelabs.rcs.core.ims.service.presence.PresenceInfo;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.ContactUtils;
import com.orangelabs.rcs.utils.StringUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Contains utility methods for interfacing with the Android SDK ContactsProvider.
 *
 * @author Jean-Marc AUFFRET
 * @author Deutsche Telekom AG
 */
public final class ContactsManager {
	/**
	 * Current instance
	 */
	private static ContactsManager instance;
	
	/**
	 * Context
	 */
	private Context ctx;
	
	private ContentResolver mResolver;
	
    /** 
     * Constant for invalid id. 
     */
	private static final int INVALID_ID = -1;

	// @formatter:off
	private enum MimeType {
		NUMBER, RCS_STATUS, REGISTRATION_STATE, CAPABILITY_IMAGE_SHARING, CAPABILITY_VIDEO_SHARING, CAPABILITY_IM_SESSION, 
		CAPABILITY_FILE_TRANSFER, CAPABILITY_GEOLOCATION_PUSH, CAPABILITY_EXTENSIONS, CAPABILITY_IP_VOICE_CALL, CAPABILITY_IP_VIDEO_CALL
	};
	// @formatter:on
	
    /** 
     * MIME type for contact number
     */
    private static final String MIMETYPE_NUMBER = ContactsProvider.MIME_TYPE_PHONE_NUMBER;
    
    /** 
     * MIME type for RCS status 
     */
    private static final String MIMETYPE_RCS_STATUS = "vnd.android.cursor.item/com.orangelabs.rcs.rcs-status";

    /** 
     * MIME type for RCS registration state 
     */
    private static final String MIMETYPE_REGISTRATION_STATE = ContactsProvider.MIME_TYPE_REGISTRATION_STATE;
    
    /** 
     * MIME type for GSMA_CS_IMAGE (image sharing) capability 
     */
    private static final String MIMETYPE_CAPABILITY_IMAGE_SHARING = ContactsProvider.MIME_TYPE_IMAGE_SHARING;
    
    /** 
     * MIME type for 3GPP_CS_VOICE (video sharing) capability 
     */
    private static final String MIMETYPE_CAPABILITY_VIDEO_SHARING = ContactsProvider.MIME_TYPE_VIDEO_SHARING;

    /** 
     * MIME type for RCS_IM (IM session) capability 
     */
    private static final String MIMETYPE_CAPABILITY_IM_SESSION = ContactsProvider.MIME_TYPE_IM_SESSION;

    /** 
     * MIME type for RCS_FT (file transfer) capability 
     */
    private static final String MIMETYPE_CAPABILITY_FILE_TRANSFER = ContactsProvider.MIME_TYPE_FILE_TRANSFER;

    /** 
     * MIME type for social presence capability 
     */
    private static final String MIMETYPE_CAPABILITY_GEOLOCATION_PUSH = ContactsProvider.MIME_TYPE_GEOLOC_PUSH;
    
    /** 
     * MIME type for RCS extensions 
     */
    private static final String MIMETYPE_CAPABILITY_EXTENSIONS = ContactsProvider.MIME_TYPE_EXTENSIONS;

    /** 
     * MIME type for RCS IP Voice Call capability 
     */
    private static final String MIMETYPE_CAPABILITY_IP_VOICE_CALL = ContactsProvider.MIME_TYPE_IP_VOICE_CALL;

    /** 
     * MIME type for RCS IP Video Call capability 
     */
    private static final String MIMETYPE_CAPABILITY_IP_VIDEO_CALL = ContactsProvider.MIME_TYPE_IP_VIDEO_CALL;
    
    /**
     * ONLINE available status
     */
    private static final int PRESENCE_STATUS_ONLINE = 5; //StatusUpdates.AVAILABLE;

    /**
     * OFFLINE available status
     */
    private static final int PRESENCE_STATUS_OFFLINE = 0; //StatusUpdates.OFFLINE;
    
    /**
     * NOT SET available status
     */
    private static final int PRESENCE_STATUS_NOT_SET = 1; //StatusUpdates.INVISIBLE;
    
    /**
     * Account name for SIM contacts
     */
    private static final String SIM_ACCOUNT_NAME = "com.android.contacts.sim";
    
    /**
     * Contact for "Me"
     */
    private static final String MYSELF = "myself";
    
    /**
     * Where clause to query raw contact
     */
	private static final String SELECTION_RAW_CONTACT_MIMETYPE_DATA1 = new StringBuilder(Data.RAW_CONTACT_ID).append("=? AND ")
			.append(Data.MIMETYPE).append("=? AND ").append(Data.DATA1).append("=?").toString();
  
	private static final String[] PROJECTION_PRESENCE_SHARING_STATUS = new String[] { RichAddressBookData.KEY_PRESENCE_SHARING_STATUS };
	
	/**
     * Projection to get DISPLAY_NAME from RichAddressBookProvider
     */
	private static final String[] PROJECTION_RABP_DISPLAY_NAME = new String[] { RichAddressBookData.KEY_DISPLAY_NAME };
	
	private static final String[] PROJECTION_RCS_STATUS = new String[] { RichAddressBookData.KEY_RCS_STATUS };
	
	private static final String[] PROJECTION_REGISTRATION_STATE = new String[] { RichAddressBookData.KEY_REGISTRATION_STATE };
	
	/**
     * Projection to get capabilities from RichAddressBookProvider TODO
     */
	private static final String[] PROJECTION_RABP_CAPABILITIES = new String[] { 
		RichAddressBookData.KEY_CAPABILITY_CS_VIDEO,
		RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER,
		RichAddressBookData.KEY_CAPABILITY_IMAGE_SHARING, 
		RichAddressBookData.KEY_CAPABILITY_IM_SESSION,
		RichAddressBookData.KEY_CAPABILITY_PRESENCE_DISCOVERY,
		RichAddressBookData.KEY_CAPABILITY_SOCIAL_PRESENCE,
		RichAddressBookData.KEY_CAPABILITY_GEOLOCATION_PUSH,
		RichAddressBookData.KEY_CAPABILITY_VIDEO_SHARING,
		RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_THUMBNAIL,
		RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_HTTP,
		RichAddressBookData.KEY_CAPABILITY_IP_VOICE_CALL,
		RichAddressBookData.KEY_CAPABILITY_IP_VIDEO_CALL,
		RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_SF,
		RichAddressBookData.KEY_CAPABILITY_GROUP_CHAT_SF,
		RichAddressBookData.KEY_AUTOMATA,
		RichAddressBookData.KEY_CAPABILITY_EXTENSIONS,
		RichAddressBookData.KEY_CAPABILITY_TIME_LAST_RQST,
		RichAddressBookData.KEY_CAPABILITY_TIME_LAST_REFRESH
		};

	/**
     * Projection to get CONTACT from RichAddressBookProvider
     */
	private static final String[] PROJECTION_RABP = new String[] { RichAddressBookData.KEY_CONTACT };

	private static final String SELECTION_RAPB_IM_BLOCKED = new StringBuilder(RichAddressBookData.KEY_IM_BLOCKED).append("=")
			.append(RichAddressBookData.BLOCKED_VALUE_SET).toString();

	private static final String SELECTION_RABP_FT_BLOCKED = new StringBuilder(RichAddressBookData.KEY_FT_BLOCKED).append("=")
			.append(RichAddressBookData.BLOCKED_VALUE_SET).toString();

	/**
	 * The cache for client queries
	 */
	private Map<ContactId,Capabilities> mCacheCapabilities;
	
	/**
	 * The logger
	 */
	private static final Logger logger = Logger.getLogger(ContactsManager.class.getSimpleName());
    
	/**
	 * Empty constructor : prevent caller from creating multiple instances
	 */
	private ContactsManager() {
	}
	
	/**
	 * Create instance
	 * 
	 * @param ctx Context
	 */
	public static void createInstance(Context ctx) {
		if (instance != null) {
			return;
		}
		synchronized (ContactsManager.class) {
			if (instance != null) {
				return;
			}
			instance = new ContactsManager(ctx);
			
		}
	}

	/**
	 * Returns instance
	 * 
	 * @return Instance
	 */
	public static ContactsManager getInstance() {
		return instance;
	}
	
    /**
     * Constructor
     *      
     * @param context Application context
     */
    private ContactsManager(Context context) {
    	ctx = context;
    	mResolver = ctx.getContentResolver();
    	mCacheCapabilities = new HashMap<ContactId,Capabilities>();
    }

	/**
	 * Returns my presence info from the EAB
	 * 
	 * @return Presence info or null in case of error
	 */
	public PresenceInfo getMyPresenceInfo() {
		if (logger.isActivated()) {
			logger.info("Get my presence info");
		}
		if (!RcsSettings.getInstance().isSocialPresenceSupported()){
			return new PresenceInfo();
		}
		Cursor cursor = getRawContactDataCursor(getRawContactIdForMe());
		return getContactInfoFromCursor(cursor).getPresenceInfo(); 
	}

	/**
	 * Set the info of a contact
	 * 
	 * @param newInfo New contact info
	 * @param oldInfo Old contact info
	 * @throws ContactsManagerException
	 */
	public void setContactInfo(ContactInfo newInfo, ContactInfo oldInfo) throws ContactsManagerException {
		// May be called from outside the core, so be sure the number format is international before doing the queries
		ContactId contact = newInfo.getContact();
		
		if (logger.isActivated()) {
			logger.info("Set contact info for " + contact);
		}
		
		// Update cache with new capabilities
		mCacheCapabilities.put(contact, newInfo.getCapabilities());
		
		// Check if we have an entry for the contact
		boolean hasEntryInRichAddressBook = isContactIdAssociatedWithContactInRichAddressBook(contact);

		ContentValues values = new ContentValues();
		values.put(RichAddressBookData.KEY_CONTACT, contact.toString());

        // Save RCS status
        values.put(RichAddressBookData.KEY_RCS_STATUS, newInfo.getRcsStatus().toInt());
        values.put(RichAddressBookData.KEY_RCS_STATUS_TIMESTAMP, newInfo.getRcsStatusTimestamp());

		// Save capabilities, if the contact is not registered, do not set the capability to true
		boolean isRegistered = RegistrationState.ONLINE.equals(newInfo.getRegistrationState());
		Capabilities newCapabilities = newInfo.getCapabilities();
		values.put(RichAddressBookData.KEY_CAPABILITY_CS_VIDEO, setCapabilityToColumn(newCapabilities.isCsVideoSupported() && isRegistered));
		values.put(RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER, setCapabilityToColumn(newCapabilities.isFileTransferSupported() && isRegistered));
		values.put(RichAddressBookData.KEY_CAPABILITY_IMAGE_SHARING, setCapabilityToColumn(newCapabilities.isImageSharingSupported() && isRegistered));
		values.put(RichAddressBookData.KEY_CAPABILITY_IM_SESSION, setCapabilityToColumn((newCapabilities.isImSessionSupported() && isRegistered)||(RcsSettings.getInstance().isImAlwaysOn() && newInfo.isRcsContact())));
		values.put(RichAddressBookData.KEY_CAPABILITY_PRESENCE_DISCOVERY, setCapabilityToColumn(newCapabilities.isPresenceDiscoverySupported() && isRegistered));
		values.put(RichAddressBookData.KEY_CAPABILITY_SOCIAL_PRESENCE, setCapabilityToColumn(newCapabilities.isSocialPresenceSupported() && isRegistered));
		values.put(RichAddressBookData.KEY_CAPABILITY_VIDEO_SHARING, setCapabilityToColumn(newCapabilities.isVideoSharingSupported() && isRegistered));
		values.put(RichAddressBookData.KEY_CAPABILITY_GEOLOCATION_PUSH, setCapabilityToColumn(newCapabilities.isGeolocationPushSupported() && isRegistered));
		values.put(RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_HTTP, setCapabilityToColumn(newCapabilities.isFileTransferHttpSupported() && isRegistered));
		values.put(RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_THUMBNAIL, setCapabilityToColumn(newCapabilities.isFileTransferThumbnailSupported() && isRegistered));
		values.put(RichAddressBookData.KEY_CAPABILITY_IP_VOICE_CALL, setCapabilityToColumn(newCapabilities.isIPVoiceCallSupported() && isRegistered));
		values.put(RichAddressBookData.KEY_CAPABILITY_IP_VIDEO_CALL, setCapabilityToColumn(newCapabilities.isIPVideoCallSupported() && isRegistered));
		values.put(RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_SF, setCapabilityToColumn((newCapabilities.isFileTransferStoreForwardSupported() && isRegistered) ||
				(RcsSettings.getInstance().isFtAlwaysOn() && newInfo.isRcsContact())));
		values.put(RichAddressBookData.KEY_AUTOMATA, setCapabilityToColumn(newCapabilities.isSipAutomata() && isRegistered));
		values.put(RichAddressBookData.KEY_CAPABILITY_GROUP_CHAT_SF, setCapabilityToColumn(newCapabilities.isGroupChatStoreForwardSupported() && isRegistered));

		// Save the capabilities extensions
		values.put(RichAddressBookData.KEY_CAPABILITY_EXTENSIONS,
				ServiceExtensionManager.getInstance().getExtensions(newCapabilities.getSupportedExtensions()));

		// Save capabilities timestamp
		values.put(RichAddressBookData.KEY_CAPABILITY_TIME_LAST_RQST, newCapabilities.getTimestampOfLastRequest());
		values.put(RichAddressBookData.KEY_CAPABILITY_TIME_LAST_REFRESH, newCapabilities.getTimestampOfLastRefresh());

		PhotoIcon photoIcon = null;
		
		// Save presence infos
        PresenceInfo newPresenceInfo = newInfo.getPresenceInfo();
		if (newPresenceInfo != null) {
			values.put(RichAddressBookData.KEY_PRESENCE_SHARING_STATUS, newPresenceInfo.getPresenceStatus());
			values.put(RichAddressBookData.KEY_PRESENCE_FREE_TEXT, newPresenceInfo.getFreetext());
			FavoriteLink favLink = newPresenceInfo.getFavoriteLink();
			if (favLink == null) {
				values.put(RichAddressBookData.KEY_PRESENCE_WEBLINK_NAME, "");
				values.put(RichAddressBookData.KEY_PRESENCE_WEBLINK_URL, "");
			} else {
				values.put(RichAddressBookData.KEY_PRESENCE_WEBLINK_NAME, favLink.getName());
				values.put(RichAddressBookData.KEY_PRESENCE_WEBLINK_URL, favLink.getLink());
			}

			Geoloc geoloc = newPresenceInfo.getGeoloc();
			if (geoloc == null) {
				values.put(RichAddressBookData.KEY_PRESENCE_GEOLOC_EXIST_FLAG, RichAddressBookData.FALSE_VALUE);
				values.put(RichAddressBookData.KEY_PRESENCE_GEOLOC_LATITUDE, 0);
				values.put(RichAddressBookData.KEY_PRESENCE_GEOLOC_LONGITUDE, 0);
				values.put(RichAddressBookData.KEY_PRESENCE_GEOLOC_ALTITUDE, 0);
			} else {
				values.put(RichAddressBookData.KEY_PRESENCE_GEOLOC_EXIST_FLAG, RichAddressBookData.TRUE_VALUE);
				values.put(RichAddressBookData.KEY_PRESENCE_GEOLOC_LATITUDE, geoloc.getLatitude());
				values.put(RichAddressBookData.KEY_PRESENCE_GEOLOC_LONGITUDE, geoloc.getLongitude());
				values.put(RichAddressBookData.KEY_PRESENCE_GEOLOC_ALTITUDE, geoloc.getAltitude());
			}
			values.put(RichAddressBookData.KEY_PRESENCE_TIMESTAMP, newPresenceInfo.getTimestamp());

			photoIcon = newPresenceInfo.getPhotoIcon();
			if (photoIcon == null) {
				values.put(RichAddressBookData.KEY_PRESENCE_PHOTO_ETAG, "");
				values.put(RichAddressBookData.KEY_PRESENCE_PHOTO_EXIST_FLAG, RichAddressBookData.FALSE_VALUE);
			} else {
				if (photoIcon.getContent() != null) {
					values.put(RichAddressBookData.KEY_PRESENCE_PHOTO_EXIST_FLAG, RichAddressBookData.TRUE_VALUE);
				} else {
					values.put(RichAddressBookData.KEY_PRESENCE_PHOTO_EXIST_FLAG, RichAddressBookData.FALSE_VALUE);
				}
				values.put(RichAddressBookData.KEY_PRESENCE_PHOTO_ETAG, photoIcon.getEtag());
			}
		}
		
		// Save registration state
		values.put(RichAddressBookData.KEY_REGISTRATION_STATE, newInfo.getRegistrationState().toInt());

        if (hasEntryInRichAddressBook) {
            // Update RABP
        	Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
			mResolver.update(uri, values, null, null);
        } else {
            // Insert
        	mResolver.insert(RichAddressBookData.CONTENT_URI, values);
        }

        // Save presence photo content
        if (photoIcon != null) {
            byte photoContent[] = photoIcon.getContent();
            if (photoContent != null) {
                Uri photoUri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI,
                        contact.toString());
                OutputStream outstream = null;
                try {
                    outstream = mResolver.openOutputStream(photoUri);
                    outstream.write(photoContent);
                    outstream.flush();
                } catch (IOException e) {
                    if (logger.isActivated()){
                        logger.error("Photo can't be saved",e);
                    }
                } finally {
                	if (outstream != null) {
                		try {
							outstream.close();
						} catch (Exception e2) {
						}
                	}
                }
            }
        }

        // Get all the Ids from raw contacts that have this phone number
        List<Long> rawContactIds = getRawContactIdsFromPhoneNumber(contact);
        if (rawContactIds.isEmpty()) {
            // If the number is not in the native address book, we are done.
            return;
        }

        // For each, prepare the modifications
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        for (Long rawContactId : rawContactIds) {
            // Get the associated RCS raw contact id
            long rcsRawContactId = getAssociatedRcsRawContact(rawContactId, contact);

			if (!newInfo.isRcsContact()) {
				// If the contact is not a RCS contact anymore, we have to delete the corresponding native raw contacts
    			ops.add(ContentProviderOperation.newDelete(RawContacts.CONTENT_URI)
    					.withSelection(RawContacts._ID + "=?", new String[]{Long.toString(rcsRawContactId)})
    					.build());
        		// Also delete the corresponding entries in the aggregation provider
    			mResolver.delete(AggregationData.CONTENT_URI,
    					AggregationData.KEY_RCS_RAW_CONTACT_ID + "=?", 
    					new String[]{Long.toString(rcsRawContactId)});
			} else {
    			// If the contact is still a RCS contact, we have to update the native raw contacts
    			if (rcsRawContactId == INVALID_ID) {
    				// If no RCS raw contact id is associated to the raw contact, create one with the right infos
    				rcsRawContactId = createRcsContact(newInfo, rawContactId);
    				// Nothing to modify, as the new contact will have taken the new infos
    				continue;
    			}
    			
    			// Modify the contact type
    			if (newInfo.getRcsStatus() != oldInfo.getRcsStatus()){
    				// Update data in rich address book provider
    				modifyRcsContactInProvider(contact, newInfo.getRcsStatus());
    			}
    			
    			// Modify the capabilities
    			// If the contact is not registered, do not set the capability to true
    			
    			// File transfer
    			// For FT, also check if the FT S&F is activated, for RCS contacts
    			ContentProviderOperation op = modifyMimeTypeForContact(rcsRawContactId, contact, MIMETYPE_CAPABILITY_FILE_TRANSFER, (newInfo.getCapabilities().isFileTransferSupported() && isRegistered)||(RcsSettings.getInstance().isFileTransferStoreForwardSupported() && newInfo.isRcsContact()), oldInfo.getCapabilities().isFileTransferSupported());
    			if (op!=null){
    				ops.add(op);
    			}
    			// Image sharing
    			op = modifyMimeTypeForContact(rcsRawContactId, contact, MIMETYPE_CAPABILITY_IMAGE_SHARING, newInfo.getCapabilities().isImageSharingSupported() && isRegistered, oldInfo.getCapabilities().isImageSharingSupported());
    			if (op!=null){
    				ops.add(op);
    			}
    			// IM session
    			// For IM, also check if the IM capability always on is activated, for RCS contacts
    			op = modifyMimeTypeForContact(rcsRawContactId, contact, MIMETYPE_CAPABILITY_IM_SESSION, (newInfo.getCapabilities().isImSessionSupported() && isRegistered)||(RcsSettings.getInstance().isImAlwaysOn() && newInfo.isRcsContact()), oldInfo.getCapabilities().isImSessionSupported());
    			if (op!=null){
    				ops.add(op);
    			}
    			// Video sharing
    			op = modifyMimeTypeForContact(rcsRawContactId, contact, MIMETYPE_CAPABILITY_VIDEO_SHARING, newInfo.getCapabilities().isVideoSharingSupported() && isRegistered, oldInfo.getCapabilities().isVideoSharingSupported());
    			if (op!=null){
    				ops.add(op);
    			}
    			// IP Voice call
    			op = modifyMimeTypeForContact(rcsRawContactId, contact, MIMETYPE_CAPABILITY_IP_VOICE_CALL, newInfo.getCapabilities().isIPVoiceCallSupported() && isRegistered, oldInfo.getCapabilities().isIPVoiceCallSupported());
    			if (op!=null){
    				ops.add(op);
    			}
    			// IP video call
    			op = modifyMimeTypeForContact(rcsRawContactId, contact, MIMETYPE_CAPABILITY_IP_VIDEO_CALL, newInfo.getCapabilities().isIPVideoCallSupported() && isRegistered, oldInfo.getCapabilities().isIPVideoCallSupported());
    			if (op!=null){
    				ops.add(op);
    			}
    			// Geolocation push
    			op = modifyMimeTypeForContact(rcsRawContactId, contact, MIMETYPE_CAPABILITY_GEOLOCATION_PUSH, newInfo.getCapabilities().isGeolocationPushSupported() && isRegistered, oldInfo.getCapabilities().isGeolocationPushSupported());
    			if (op!=null){
    				ops.add(op);
    			}
    			// RCS extensions
    			Set<String> extensions = newInfo.getCapabilities().getSupportedExtensions();
    			if (!isRegistered){
    				// If contact is not registered, do not put any extensions
    				extensions.clear();
    			}
    			List<ContentProviderOperation> extensionOps = modifyExtensionsCapabilityForContact(rcsRawContactId, contact, extensions, oldInfo.getCapabilities().getSupportedExtensions());
    			for (ContentProviderOperation extensionOperation : extensionOps) {
    				if (extensionOperation != null) {
						ops.add(extensionOperation);
					}
				}
    			
    			// New contact registration state
    			String newFreeText = "";
    			if (newInfo.getPresenceInfo()!=null){
    				newFreeText = newInfo.getPresenceInfo().getFreetext();
    			}
    			// Old contact registration state
    			String oldFreeText = "";
    			if (oldInfo.getPresenceInfo()!=null){
    				oldFreeText = oldInfo.getPresenceInfo().getFreetext();
    			}
    			List<ContentProviderOperation> registrationOps = modifyContactRegistrationState(rcsRawContactId, contact, newInfo.getRegistrationState(), oldInfo.getRegistrationState(), newFreeText, oldFreeText);
				for (ContentProviderOperation registrationOperation : registrationOps) {
					if (registrationOperation != null) {
						ops.add(registrationOperation);
					}
				}
    
    			// Presence fields
    			List<ContentProviderOperation> presenceOps = modifyPresenceForContact(rcsRawContactId, contact, newInfo.getPresenceInfo(), oldInfo.getPresenceInfo());
				for (ContentProviderOperation presenceOperation : presenceOps) {
					if (presenceOperation != null) {
						ops.add(presenceOperation);
					}
				}
			}
		}
		
		if (!ops.isEmpty()){
			// Do the actual database modifications
			try {
				mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
			} catch (RemoteException e) {
				if (logger.isActivated()){
					logger.error("Something went wrong when updating the database with the contact info",e);
				}
				throw new ContactsManagerException(e.getMessage());
			} catch (OperationApplicationException e) {
				if (logger.isActivated()){
					logger.error("Something went wrong when updating the database with the contact info",e);
				}
				throw new ContactsManagerException(e.getMessage());
			}
		}
	}

	/**
	 * Get the infos of a contact in the EAB
	 *  	
	 * @param contact Contact
	 * @return Contact info
	 */
	public ContactInfo getContactInfo(ContactId contact) {
		ContactInfo infos = new ContactInfo();
		infos.setRcsStatus(RcsStatus.NO_INFO);
		infos.setRcsStatusTimestamp(System.currentTimeMillis());
		infos.setContact(contact);
		
		// Try get capabilities from cache
		Capabilities capabilities = mCacheCapabilities.get(contact);
	
		PresenceInfo presenceInfo = new PresenceInfo();
		
		infos.setRegistrationState(RegistrationState.UNKNOWN);

		Cursor cur = null;
		try {
			Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
			cur = mResolver.query(uri, null, null, null, null);
			if (cur.moveToFirst()) {
				// Get RCS display name
				infos.setDisplayName(cur.getString(cur.getColumnIndex(RichAddressBookData.KEY_DISPLAY_NAME)));
				
				// Get RCS Status
				int rcsStatus = cur.getInt(cur.getColumnIndex(RichAddressBookData.KEY_RCS_STATUS));
				infos.setRcsStatus(RcsStatus.valueOf(rcsStatus));
				
				infos.setRcsStatusTimestamp(cur.getLong(cur.getColumnIndex(RichAddressBookData.KEY_RCS_STATUS_TIMESTAMP)));
				
				int registrationState = cur.getInt(cur.getColumnIndex(RichAddressBookData.KEY_REGISTRATION_STATE));
				infos.setRegistrationState(RegistrationState.valueOf(registrationState));

				// Get Presence info
				presenceInfo.setPresenceStatus(cur.getString(cur.getColumnIndex(RichAddressBookData.KEY_PRESENCE_SHARING_STATUS)));

				FavoriteLink favLink = new FavoriteLink(cur.getString(cur
						.getColumnIndex(RichAddressBookData.KEY_PRESENCE_WEBLINK_NAME)), cur.getString(cur
						.getColumnIndex(RichAddressBookData.KEY_PRESENCE_WEBLINK_URL)));
				presenceInfo.setFavoriteLink(favLink);
				presenceInfo.setFavoriteLinkUrl(favLink.getLink());

				presenceInfo.setFreetext(cur.getString(cur.getColumnIndex(RichAddressBookData.KEY_PRESENCE_FREE_TEXT)));

				Geoloc geoloc = null;
				if (Boolean.parseBoolean(cur.getString(cur.getColumnIndex(RichAddressBookData.KEY_PRESENCE_GEOLOC_EXIST_FLAG)))) {
					geoloc = new Geoloc(cur.getDouble(cur.getColumnIndex(RichAddressBookData.KEY_PRESENCE_GEOLOC_LATITUDE)),
							cur.getDouble(cur.getColumnIndex(RichAddressBookData.KEY_PRESENCE_GEOLOC_LONGITUDE)), cur.getDouble(cur
									.getColumnIndex(RichAddressBookData.KEY_PRESENCE_GEOLOC_ALTITUDE)));
				}
				presenceInfo.setGeoloc(geoloc);

				presenceInfo.setTimestamp(cur.getLong(cur.getColumnIndex(RichAddressBookData.KEY_PRESENCE_TIMESTAMP)));

				PhotoIcon photoIcon = null;
				if (Boolean.parseBoolean(cur.getString(cur.getColumnIndex(RichAddressBookData.KEY_PRESENCE_PHOTO_EXIST_FLAG)))) {
					try {
						Uri photoUri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
						String etag = cur.getString(cur.getColumnIndex(RichAddressBookData.KEY_PRESENCE_PHOTO_ETAG));
						InputStream stream = mResolver.openInputStream(photoUri);
						byte[] content = new byte[stream.available()];
						stream.read(content, 0, content.length);
						Bitmap bmp = BitmapFactory.decodeByteArray(content, 0, content.length);
						if (bmp != null) {
							photoIcon = new PhotoIcon(content, bmp.getWidth(), bmp.getHeight(), etag);
						}
					} catch (IOException e) {
						if (logger.isActivated()) {
							logger.error("Can't get the photo", e);
						}
					}
				}
				presenceInfo.setPhotoIcon(photoIcon);

				if (capabilities == null) {
					// Capabilities are not cached for this contact
					capabilities = new Capabilities();
					// Get the capabilities infos
					capabilities.setCsVideoSupport(getCapabilityFromColumn(cur, RichAddressBookData.KEY_CAPABILITY_CS_VIDEO));
					capabilities.setFileTransferSupport(getCapabilityFromColumn(cur,
							RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER));
					capabilities.setImageSharingSupport(getCapabilityFromColumn(cur,
							RichAddressBookData.KEY_CAPABILITY_IMAGE_SHARING));
					capabilities.setImSessionSupport(getCapabilityFromColumn(cur, RichAddressBookData.KEY_CAPABILITY_IM_SESSION));
					capabilities.setPresenceDiscoverySupport(getCapabilityFromColumn(cur,
							RichAddressBookData.KEY_CAPABILITY_PRESENCE_DISCOVERY));
					capabilities.setSocialPresenceSupport(getCapabilityFromColumn(cur,
							RichAddressBookData.KEY_CAPABILITY_SOCIAL_PRESENCE));
					capabilities.setGeolocationPushSupport(getCapabilityFromColumn(cur,
							RichAddressBookData.KEY_CAPABILITY_GEOLOCATION_PUSH));
					capabilities.setVideoSharingSupport(getCapabilityFromColumn(cur,
							RichAddressBookData.KEY_CAPABILITY_VIDEO_SHARING));
					capabilities.setFileTransferThumbnailSupport(getCapabilityFromColumn(cur,
							RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_THUMBNAIL));
					capabilities.setFileTransferHttpSupport(getCapabilityFromColumn(cur,
							RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_HTTP));
					capabilities.setIPVoiceCallSupport(getCapabilityFromColumn(cur,
							RichAddressBookData.KEY_CAPABILITY_IP_VOICE_CALL));
					capabilities.setIPVideoCallSupport(getCapabilityFromColumn(cur,
							RichAddressBookData.KEY_CAPABILITY_IP_VIDEO_CALL));
					capabilities.setFileTransferStoreForwardSupport(getCapabilityFromColumn(cur,
							RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_SF));
					capabilities.setGroupChatStoreForwardSupport(getCapabilityFromColumn(cur,
							RichAddressBookData.KEY_CAPABILITY_GROUP_CHAT_SF));
					capabilities.setSipAutomata(getCapabilityFromColumn(cur, RichAddressBookData.KEY_AUTOMATA));

					// Set RCS extensions capability
					capabilities.setSupportedExtensions(ServiceExtensionManager.getInstance().getExtensions(
							cur.getString(cur.getColumnIndex(RichAddressBookData.KEY_CAPABILITY_EXTENSIONS))));

					// Set time of last request
					capabilities.setTimestampOfLastRequest(cur.getLong(cur
							.getColumnIndex(RichAddressBookData.KEY_CAPABILITY_TIME_LAST_RQST)));
					// Set time of last refresh
					capabilities.setTimestampOfLastRefresh(cur.getLong(cur
							.getColumnIndex(RichAddressBookData.KEY_CAPABILITY_TIME_LAST_REFRESH)));
					
					// Insert into cache
					mCacheCapabilities.put(contact, capabilities);
				}
			}
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Internal exception for contact "+contact, e);
			}
			// Clear cache entry
			mCacheCapabilities.remove(contact);
		} finally {
			if (cur != null) {
				cur.close();
			}
		}
		infos.setPresenceInfo(presenceInfo);
		infos.setCapabilities(capabilities);

		return infos;
	}
	
	/**
	 * Get sharing status of a contact
	 *  
	 * @param contact Contact
	 * @return sharing status or NO_INFO if cannot be retrieved
	 */
	private RcsStatus getContactSharingStatus(ContactId contact) {
		if (logger.isActivated()) {
			logger.info("Get sharing status for contact " + contact);
		}

		Cursor cursor = null;
		try {
			// Get this number status in address book provider
			Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
			cursor = mResolver.query(uri, PROJECTION_PRESENCE_SHARING_STATUS, null, null, null);
			if (cursor.moveToFirst()) {
				int status = cursor.getInt(cursor.getColumnIndex(RichAddressBookData.KEY_PRESENCE_SHARING_STATUS));
				return RcsStatus.valueOf(status);
			}
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Internal exception", e);
			}
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return RcsStatus.NO_INFO;
	}

	/**
	 * Block a contact
	 * 
	 * @param contact Contact
	 * @throws ContactsManagerException
	 */	
	public void blockContact(ContactId contact) throws ContactsManagerException {
		if (logger.isActivated()) {
			logger.info("Block contact " + contact);
		}
		try{
			// Go to RCS_BLOCKED state
			ContactInfo oldInfo = getContactInfo(contact);
			ContactInfo newInfo = new ContactInfo(oldInfo);
			newInfo.setRcsStatus(RcsStatus.BLOCKED);
			setContactInfo(newInfo, oldInfo);
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Internal exception", e);
			}
			throw new ContactsManagerException(e.getMessage());
		}
	}

	/**
	 * Flush the rich address book provider
	 */
	public void flushContactProvider() {
		mCacheCapabilities.clear();
		mResolver.delete(RichAddressBookData.CONTENT_URI, null, null);
	}
	
	/**
	 * Add or modify a contact number to the rich address book provider
	 * 
	 * @param contact Contact ID
	 * @param RCS status
	 */
	public void modifyRcsContactInProvider(ContactId contact, RcsStatus rcsStatus) {
		ContentValues values = new ContentValues();
		values.put(RichAddressBookData.KEY_PRESENCE_SHARING_STATUS, rcsStatus.toInt());
		values.put(RichAddressBookData.KEY_TIMESTAMP, System.currentTimeMillis());
		if (!isContactIdAssociatedWithContactInRichAddressBook(contact)) {
			values.put(RichAddressBookData.KEY_CONTACT, contact.toString());
			// Contact not present in provider, insert
			mResolver.insert(RichAddressBookData.CONTENT_URI, values);
		} else {
			// Contact already present, update
			Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
			mResolver.update(uri, values, null,	null);
		}
	}

	/**
	 * Get the RCS contacts in the rich address book provider which have a presence relationship with the user
	 * 
	 * @return set containing all RCS contacts, "Me" item excluded 
	 */
	public Set<ContactId> getRcsContactsWithSocialPresence() {
		Set<ContactId> rcsNumbers = new HashSet<ContactId>();
		// Filter the RCS status
		String selection = "(" + RichAddressBookData.KEY_RCS_STATUS + "<>? AND " + RichAddressBookData.KEY_RCS_STATUS + "<>? AND "
				+ RichAddressBookData.KEY_RCS_STATUS + "<>? )";
		String[] selectionArgs = { Integer.toString(RcsStatus.NO_INFO.toInt()), Integer.toString(RcsStatus.NOT_RCS.toInt()),
				Integer.toString(RcsStatus.RCS_CAPABLE.toInt()) };
		Cursor c = null;
		try {
			c = mResolver.query(RichAddressBookData.CONTENT_URI, PROJECTION_RABP, selection, selectionArgs, null);
			while (c.moveToNext()) {
				try {
					rcsNumbers.add(ContactUtils.createContactId(c.getString(c.getColumnIndex(RichAddressBookData.KEY_CONTACT))));
				} catch (RcsContactFormatException e1) {
					if (logger.isActivated()) {
						logger.warn("Cannot parse number "+c.getString(c.getColumnIndex(RichAddressBookData.KEY_CONTACT)));
					}
				}
			}
		} catch (Exception e) {
		} finally {
			if (c != null) {
				c.close();
			}
		}
		return rcsNumbers;
	}

	/**
	 * Get the RCS contacts in the contact contract provider
	 *
	 * @return set containing all RCS contacts 
	 */
	public Set<ContactId> getRcsContacts() {
		Set<ContactId> rcsNumbers = new HashSet<ContactId>();
		String[] projection = { RichAddressBookData.KEY_CONTACT };
		// Filter the RCS status
		String selection = "(" + RichAddressBookData.KEY_RCS_STATUS + "<>? AND " + RichAddressBookData.KEY_RCS_STATUS + "<>? )";
		String[] selectionArgs = { Integer.toString(RcsStatus.NO_INFO.toInt()), Integer.toString(RcsStatus.NOT_RCS.toInt()) };
		Cursor cursor = null;
		try {
			cursor = mResolver.query(RichAddressBookData.CONTENT_URI, projection, selection, selectionArgs, null);
			while (cursor.moveToNext()) {
				try {
					rcsNumbers.add(ContactUtils.createContactId(cursor.getString(cursor.getColumnIndex(RichAddressBookData.KEY_CONTACT))));
				} catch (RcsContactFormatException e1) {
					if (logger.isActivated()) {
						logger.warn("Cannot parse number " + cursor.getString(cursor.getColumnIndex(RichAddressBookData.KEY_CONTACT)));
					}
				}
			}
		} catch (Exception e) {
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return rcsNumbers;
	}

	/**
	 * Get all the contacts in the rich address book provider
	 *
	 * @return set containing all contacts that have been at least queried once for capabilities
	 */
	public Set<ContactId> getAllContacts() {
		Set<ContactId> numbers = new HashSet<ContactId>();
		Cursor cur = null;
		try {
			cur = mResolver.query(RichAddressBookData.CONTENT_URI, PROJECTION_RABP, null, null, null);
			while (cur.moveToNext()) {
				try {
					numbers.add(ContactUtils.createContactId(cur.getString(cur.getColumnIndex(RichAddressBookData.KEY_CONTACT))));
				} catch (RcsContactFormatException e1) {
					if (logger.isActivated()) {
						logger.warn("Cannot parse contact " + cur.getString(cur.getColumnIndex(RichAddressBookData.KEY_CONTACT)));
					}
				}
			}
		} catch (Exception e) {
		} finally {
			if (cur != null) {
				cur.close();
			}
		}
		return numbers;
	}
	
	/**
	 * Is the number in the RCS blocked list
	 * 
	 * @param contact contact to check
	 * @return boolean
	 */
	public boolean isNumberBlocked(ContactId contact) {
		return RcsStatus.BLOCKED.equals(getContactSharingStatus(contact));
	}
	
	/**
	 * Is the number in the RCS buddy list
	 * 
	 * @param contact contact to check
	 * @return boolean
	 */
	public boolean isNumberShared(ContactId contact) {
		return RcsStatus.ACTIVE.equals(getContactSharingStatus(contact));
	}

	/**
	 * Has the number been invited to RCS
	 * 
	 * @param contact contact to check
	 * @return boolean
	 */
	public boolean isNumberInvited(ContactId contact) {
		return RcsStatus.PENDING.equals(getContactSharingStatus(contact));
	}

	/**
	 * Has the number invited us to RCS
	 * 
	 * @param contact contact to check
	 * @return boolean
	 */
	public boolean isNumberWilling(ContactId contact) {
		return RcsStatus.PENDING_OUT.equals(getContactSharingStatus(contact));
	}
	
	/**
	 * Has the number invited us to RCS then be cancelled
	 * 
	 * @param contact contact to check
	 * @return boolean
	 */
	public boolean isNumberCancelled(ContactId contact) {
		return RcsStatus.CANCELLED.equals(getContactSharingStatus(contact));
	}
    
	/**
	 * Modify the corresponding mimetype row for the contact
	 * 
	 * @param rawContactId Raw contact id of the RCS contact
	 * @param number RCS number of the contact
	 * @param mimeType Mime type associated to the capability
	 * @param newState True if the capability must be enabled, else false
	 * @param oldState True if the capability was enabled, else false
	 * @return ContentProviderOperation to be done
	 */
	private ContentProviderOperation modifyMimeTypeForContact(long rawContactId, ContactId rcsNumber, String mimeType,
			boolean newState, boolean oldState) {

		if (newState == oldState) {
			// Nothing to do
			return null;
		}
		if (newState == true) {
			// We have to insert a new data in the raw contact
			return insertMimeTypeForContact(rawContactId, rcsNumber, mimeType);
		} else {
			// We have to remove the data from the raw contact
			return deleteMimeTypeForContact(rawContactId, rcsNumber, mimeType);
		}
	}

    /**
     * Create (first time) the corresponding mimetype row for the contact
     *
     * @param rawContactId
     * @param rcsNumber
     * @param mimeType
     * @return ContentProviderOperation to be done
     */
    private ContentProviderOperation createMimeTypeForContact(int rawContactId, ContactId rcsNumber, String mimeType) {
        String mimeTypeDescription = getMimeTypeDescription(mimeType);
        if (mimeTypeDescription != null) {
            // Check if there is a mimetype description to be added
            return ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(Data.MIMETYPE, mimeType)
                    .withValue(Data.DATA1, rcsNumber.toString())
                    .withValue(Data.DATA2, mimeTypeDescription)
                    .withValue(Data.DATA3, rcsNumber.toString())
                    .build();
        } else {
            return ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(Data.MIMETYPE, mimeType)
                    .withValue(Data.DATA1, rcsNumber.toString())
                    .build();
        }
    }

    /**
     * Insert the corresponding mimetype row for the contact
     *
     * @param rawContactId
     * @param rcsNumber
     * @param mimeType
     * @return ContentProviderOperation to be done
     */
    private ContentProviderOperation insertMimeTypeForContact(long rawContactId, ContactId rcsNumber, String mimeType) {
        String mimeTypeDescription = getMimeTypeDescription(mimeType);
        if (mimeTypeDescription != null) {
            // Check if there is a mimetype description to be added
            return ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(Data.MIMETYPE, mimeType)
                    .withValue(Data.DATA1, rcsNumber.toString())
                    .withValue(Data.DATA2, mimeTypeDescription)
                    .withValue(Data.DATA3, rcsNumber.toString())
                    .build();
        } else {
            return ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(Data.MIMETYPE, mimeType)
                    .withValue(Data.DATA1, rcsNumber.toString())
                    .build();
        }
    }

	/**
	 * Remove the corresponding mimetype row for the contact
	 * 
	 * @param rawContactId
	 * @param rcsNumber
	 * @param mimeType
	 * @return ContentProviderOperation to be done
	 */
	private ContentProviderOperation deleteMimeTypeForContact(long rawContactId, ContactId rcsNumber, String mimeType){
		// We have to remove a data from the raw contact
		return ContentProviderOperation.newDelete(Data.CONTENT_URI)
        .withSelection(SELECTION_RAW_CONTACT_MIMETYPE_DATA1, new String[]{String.valueOf(rawContactId), mimeType, rcsNumber.toString()})
        .build();		
	}
	
	/**
	 * Modify the registration state for the contact
	 * 
	 * @param rawContactId Raw contact id of the RCS contact
	 * @param number RCS number of the contact
	 * @param newRegistrationState
	 * @param oldRegistrationState
	 * @param newFreeText
	 * @param oldFreeText
	 * @return list of ContentProviderOperations to be done
	 */
	private List<ContentProviderOperation> modifyContactRegistrationState(long rawContactId, ContactId rcsNumber, RegistrationState newRegistrationState, RegistrationState oldRegistrationState, String newFreeText, String oldFreeText){
		
		List<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
		boolean registrationChanged = true;
		if ((newRegistrationState == oldRegistrationState || RegistrationState.UNKNOWN == newRegistrationState)){
			registrationChanged = false;			
		}
		
		if (registrationChanged){
			// Modify registration status
			ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
					.withSelection(SELECTION_RAW_CONTACT_MIMETYPE_DATA1, new String[]{Long.toString(rawContactId), MIMETYPE_REGISTRATION_STATE, rcsNumber.toString()})
					.withValue(Data.DATA2, newRegistrationState)
					.build());
		}
		
		if (!StringUtils.equals(newFreeText, oldFreeText) || registrationChanged){
			int availability = PRESENCE_STATUS_NOT_SET;
			if (RegistrationState.ONLINE == newRegistrationState) {
				availability = PRESENCE_STATUS_ONLINE;
			} else if (RegistrationState.OFFLINE == newRegistrationState) {
				availability = PRESENCE_STATUS_OFFLINE;
			}

			// Get the id of the status update data linked to this raw contact id
			String[] projection = {Data._ID, Data.RAW_CONTACT_ID};

			long dataId = INVALID_ID;
			String selection = new StringBuilder(Data.RAW_CONTACT_ID).append("=?").toString();
			String[] selectionArgs = { Long.toString(rawContactId)};
			Cursor cur = null;
			try {
				cur = mResolver.query(Data.CONTENT_URI, projection, selection, selectionArgs, null);
				if (cur.moveToNext()) {
					dataId = cur.getLong(cur.getColumnIndex(Data._ID));
				}
			} catch (Exception e) {
			} finally {
				if (cur != null) {
					cur.close();
				}
			}

			ops.add(ContentProviderOperation.newInsert(StatusUpdates.CONTENT_URI)
					.withValue(StatusUpdates.DATA_ID, dataId)
					.withValue(StatusUpdates.STATUS, newFreeText)
					.withValue(StatusUpdates.STATUS_RES_PACKAGE, ctx.getPackageName())
					.withValue(StatusUpdates.STATUS_LABEL, R.string.rcs_core_account_id)
					.withValue(StatusUpdates.STATUS_ICON, R.drawable.rcs_icon)
					.withValue(StatusUpdates.PRESENCE, availability)
					// Needed for inserting PRESENCE
					.withValue(StatusUpdates.PROTOCOL, Im.PROTOCOL_CUSTOM)
					.withValue(StatusUpdates.CUSTOM_PROTOCOL, " " /* Intentional left blank */)
					.withValue(StatusUpdates.STATUS_TIMESTAMP, System.currentTimeMillis())
					.build());
		}
		
		return ops;
	}
	
	/**
	 * Modify the registration state for the contact
	 * 
	 * @param rawContactId Raw contact id of the RCS contact
	 * @param newRegistrationState
	 * @param oldRegistrationState
	 * @param newFreeText
	 * @param oldFreeText
	 * @return list of ContentProviderOperations to be done
	 */
	private List<ContentProviderOperation> modifyContactRegistrationStateForMyself(long rawContactId,
			RegistrationState newRegistrationState, RegistrationState oldRegistrationState, String newFreeText, String oldFreeText) {

		List<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
		boolean registrationChanged = true;
		if (newRegistrationState == oldRegistrationState || RegistrationState.UNKNOWN.equals(newRegistrationState)) {
			registrationChanged = false;
		}

		if (registrationChanged) {
			// Modify registration status
			ops.add(ContentProviderOperation
					.newUpdate(Data.CONTENT_URI)
					.withSelection(SELECTION_RAW_CONTACT_MIMETYPE_DATA1,
							new String[] { Long.toString(rawContactId), MIMETYPE_REGISTRATION_STATE, MYSELF })
					.withValue(Data.DATA2, newRegistrationState).build());
		}

		if (!StringUtils.equals(newFreeText, oldFreeText) || registrationChanged) {
			int availability = PRESENCE_STATUS_NOT_SET;
			if (RegistrationState.ONLINE.equals(newRegistrationState)) {
				availability = PRESENCE_STATUS_ONLINE;
			} else if (RegistrationState.OFFLINE.equals(newRegistrationState)) {
				availability = PRESENCE_STATUS_OFFLINE;
			}

			// Get the id of the status update data linked to this raw contact id
			long dataId = INVALID_ID;
			Cursor cur = getRawContactDataCursor(rawContactId);
			if (cur != null) {
				dataId = cur.getLong(cur.getColumnIndex(Data._ID));
			}

			ops.add(ContentProviderOperation.newInsert(StatusUpdates.CONTENT_URI).withValue(StatusUpdates.DATA_ID, dataId)
					.withValue(StatusUpdates.STATUS, newFreeText).withValue(StatusUpdates.STATUS_RES_PACKAGE, ctx.getPackageName())
					.withValue(StatusUpdates.STATUS_LABEL, R.string.rcs_core_account_id)
					.withValue(StatusUpdates.STATUS_ICON, R.drawable.rcs_icon).withValue(StatusUpdates.PRESENCE, availability)
					// Needed for inserting PRESENCE
					.withValue(StatusUpdates.PROTOCOL, Im.PROTOCOL_CUSTOM).withValue(StatusUpdates.CUSTOM_PROTOCOL,
					// Intentional left blank
							" ").withValue(StatusUpdates.STATUS_TIMESTAMP, System.currentTimeMillis()).build());
		}
		return ops;
	}
	
	/**
	 * Modify the RCS extensions capability for the contact
	 * 
	 * @param rawContactId Raw contact id of the RCS contact
	 * @param contact RCS number of the contact
	 * @param newExtensions New extensions capabilities
	 * @param oldExtensions Old extensions capabilities 
	 * @return list of contentProviderOperation to be done
	 */
	private List<ContentProviderOperation> modifyExtensionsCapabilityForContact(long rawContactId, ContactId contact,
			Set<String> newExtensions, Set<String> oldExtensions) {
		List<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
		// Compare the two lists of extensions
		if (newExtensions.containsAll(oldExtensions) && oldExtensions.containsAll(newExtensions)) {
			// Both lists have the same tags, no need to update
			return ops;
		}

		// Update extensions
		ops.add(ContentProviderOperation
				.newUpdate(Data.CONTENT_URI)
				.withSelection(SELECTION_RAW_CONTACT_MIMETYPE_DATA1,
						new String[] { String.valueOf(rawContactId), MIMETYPE_CAPABILITY_EXTENSIONS, contact.toString() })
				.withValue(Data.DATA2, ServiceExtensionManager.getInstance().getExtensions(newExtensions)).build());
		return ops;
	}
	
	/**
	 * Modify the presence info for a contact
	 * 
	 * @param rawContactId Raw contact id of the RCS contact
	 * @param contact RCS number of the contact
	 * @param newPresenceInfo
	 * @param oldPresenceInfo
	 * @return list of ContentProviderOperation to be done
	 */
	private List<ContentProviderOperation> modifyPresenceForContact(long rawContactId, ContactId contact, PresenceInfo newPresenceInfo, PresenceInfo oldPresenceInfo){
    	List<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

    	if (newPresenceInfo!=null && oldPresenceInfo!=null){

			if (!StringUtils.equals(newPresenceInfo.getFavoriteLinkUrl(), oldPresenceInfo.getFavoriteLinkUrl())) {
    			
        		//Add the weblink to the native @book
    			ContentValues values = new ContentValues();
    			values.put(Data.RAW_CONTACT_ID, rawContactId);
    			values.put(Website.URL, newPresenceInfo.getFavoriteLinkUrl());
    			values.put(Website.TYPE, Website.TYPE_HOMEPAGE);
    			values.put(Data.IS_PRIMARY, 1);
   				values.put(Data.IS_SUPER_PRIMARY, 1);

				// Get the id of the current weblink mimetype
				long currentNativeWebLinkDataId = INVALID_ID;
				Cursor cur = null;
				try {
					cur = mResolver.query(Data.CONTENT_URI, new String[] { Data._ID },
							Data.RAW_CONTACT_ID + "=? AND " + Website.TYPE + "=?",
							new String[] { Long.toString(rawContactId), String.valueOf(Website.TYPE_HOMEPAGE) },
							null);
					if (cur.moveToNext()) {
						currentNativeWebLinkDataId = cur.getLong(cur.getColumnIndex(Data._ID));
					}
				} catch (Exception e) {
				} finally {
					if (cur != null) {
						cur.close();
					}
				}
   				
    			if (oldPresenceInfo.getFavoriteLinkUrl()==null){
    				// There was no weblink, insert
        			ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
        					.withValues(values)
        					.build());
    			}else if (newPresenceInfo.getFavoriteLinkUrl()!=null){
    				// Update the existing weblink
    				ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
    					.withSelection(Data._ID + "=?", new String[]{String.valueOf(currentNativeWebLinkDataId)})
    					.withValues(values)
    					.build());
    			}else{
    				// Remove the existing weblink
    				ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI)
        					.withSelection(Data._ID + "=?", new String[]{String.valueOf(currentNativeWebLinkDataId)})
        					.build());
    			}
    		}

    		// Set the photo-icon
    		PhotoIcon oldPhotoIcon = oldPresenceInfo.getPhotoIcon();
    		PhotoIcon newPhotoIcon = newPresenceInfo.getPhotoIcon();
    		// Check if photo etags are the same between the two presenceInfo
    		boolean haveSameEtags = false;
    		String oldPhotoIconEtag = null;
    		String newPhotoIconEtag = null;
    		if (oldPhotoIcon!=null){
    			oldPhotoIconEtag = oldPhotoIcon.getEtag();
    		}
			if (newPhotoIcon != null) {
				newPhotoIconEtag = newPhotoIcon.getEtag();
			}
			if (oldPhotoIconEtag == null && newPhotoIconEtag == null) {
				haveSameEtags = true;
			} else if (oldPhotoIconEtag != null && newPhotoIconEtag != null) {
				haveSameEtags = (oldPhotoIconEtag.equalsIgnoreCase(newPhotoIconEtag));
			}

			if (!haveSameEtags) {
				// Not the same etag, so photo changed
				// Replace photo and etag
				List<ContentProviderOperation> photoOps = setContactPhoto(rawContactId, newPhotoIcon, true);
				for (ContentProviderOperation photoOp : photoOps) {
					if (photoOp != null) {
						ops.add(photoOp);
					}
				}
			}

    	} else if (newPresenceInfo != null) {
			// The new presence info is not null but the old one was, add new fields
			RegistrationState availability = RegistrationState.UNKNOWN;
			if (newPresenceInfo.isOnline()) {
				availability = RegistrationState.ONLINE;
			} else if (newPresenceInfo.isOffline()) {
				availability = RegistrationState.OFFLINE;
			}
    		
    		// Add the presence status to native address book
			List<ContentProviderOperation> registrationStateOps = modifyContactRegistrationState(rawContactId, contact,
					availability, RegistrationState.UNKNOWN, newPresenceInfo.getFreetext(), "");
			for (ContentProviderOperation registrationStateOp : registrationStateOps) {
				if (registrationStateOp != null){
    				ops.add(registrationStateOp);
    			}
			}

    		//Add the weblink to the native @book
			ContentValues values = new ContentValues();
			values.put(Data.RAW_CONTACT_ID, rawContactId);
			values.put(Website.URL, newPresenceInfo.getFavoriteLinkUrl());
			values.put(Website.TYPE, Website.TYPE_HOMEPAGE);
			values.put(Data.IS_PRIMARY, 1);
			values.put(Data.IS_SUPER_PRIMARY, 1);

			// Get the id of the current weblink mimetype
			long currentNativeWebLinkDataId = INVALID_ID;
			Cursor cur = null;
			try {
				cur = mResolver.query(Data.CONTENT_URI,
								new String[] { Data._ID },
								Data.RAW_CONTACT_ID + "=? AND " + Website.TYPE + "=?",
								new String[] { Long.toString(rawContactId), String.valueOf(Website.TYPE_HOMEPAGE) },
								null);
				if (cur.moveToNext()) {
					currentNativeWebLinkDataId = cur.getLong(cur.getColumnIndex(Data._ID));
				}
			} catch (Exception e) {
			} finally {
				if (cur != null) {
					cur.close();
				}
			}
				
			if (oldPresenceInfo.getFavoriteLinkUrl()==null){
				// There was no weblink, insert
    			ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
    					.withValues(values)
    					.build());
			}else if (newPresenceInfo.getFavoriteLinkUrl()!=null){
				// Update the existing weblink
				ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
					.withSelection(Data._ID + "=?", new String[]{String.valueOf(currentNativeWebLinkDataId)})
					.withValues(values)
					.build());
			}else{
				// Remove the existing weblink
				ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI)
    					.withSelection(Data._ID + "=?", new String[]{String.valueOf(currentNativeWebLinkDataId)})
    					.build());
			}

			// Set the photo
			List<ContentProviderOperation> photoOps = setContactPhoto(rawContactId, newPresenceInfo.getPhotoIcon(), true);
			for (ContentProviderOperation photoOp : photoOps) {
				if (photoOp != null) {
					ops.add(photoOp);
				}
			}

		} else {
			if (oldPresenceInfo != null) {
				// The new presence info is null but the old one was not, remove fields

				// Remove the presence status to native address book
				// Force presence status to offline and free text to null
				List<ContentProviderOperation> registrationStateOps = modifyContactRegistrationState(rawContactId, contact,
						RegistrationState.OFFLINE, RegistrationState.UNKNOWN, "", oldPresenceInfo.getFreetext());
				for (ContentProviderOperation registrationStateOp : registrationStateOps) {
					if (registrationStateOp != null) {
						ops.add(registrationStateOp);
					}
				}

				// Remove presence web link in native address book
				// Add the weblink to the native @book
				// Get the id of the current weblink mimetype
				long currentNativeWebLinkDataId = INVALID_ID;
				Cursor cur = null;
				try {
					cur = mResolver.query(Data.CONTENT_URI, new String[] { Data._ID },
							Data.RAW_CONTACT_ID + "=? AND " + Website.TYPE + "=?",
							new String[] { Long.toString(rawContactId), String.valueOf(Website.TYPE_HOMEPAGE) }, null);
					if (cur.moveToNext()) {
						currentNativeWebLinkDataId = cur.getLong(cur.getColumnIndex(Data._ID));
					}
				} catch (Exception e) {
				} finally {
					if (cur != null) {
						cur.close();
					}
				}

				ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI)
						.withSelection(Data._ID + "=?", new String[] { String.valueOf(currentNativeWebLinkDataId) }).build());

				// Set the photo
				List<ContentProviderOperation> photoOps = setContactPhoto(rawContactId, null, true);
				for (ContentProviderOperation photoOp : photoOps) {
					if (photoOp != null) {
						ops.add(photoOp);
					}
				}
			}
		}
    	
    	return ops;
	}
	
	/**
	 * Get description associated to a MIME type. This string will be visible in the contact card
	 * 
	 * @param mimeType MIME type
	 * @return String
	 */
	private String getMimeTypeDescription(String mimeType){
		if (mimeType.equalsIgnoreCase(MIMETYPE_CAPABILITY_FILE_TRANSFER)) {
			return ctx.getString(R.string.rcs_core_contact_file_transfer);
		} else {
			if (mimeType.equalsIgnoreCase(MIMETYPE_CAPABILITY_IM_SESSION)) {
				return ctx.getString(R.string.rcs_core_contact_im_session);
			} else {
				if (mimeType.equalsIgnoreCase(MIMETYPE_CAPABILITY_IP_VOICE_CALL)) {
					return ctx.getString(R.string.rcs_core_contact_ip_voice_call);
				} else {
					if (mimeType.equalsIgnoreCase(MIMETYPE_CAPABILITY_IP_VIDEO_CALL)) {
						return ctx.getString(R.string.rcs_core_contact_ip_video_call);
					} else
						return null;
				}
			}
		}
	}
	
	/**
	 * Set contact capabilities
	 * 
	 * @param contact Contact Id
	 * @param capabilities Capabilities
	 * @param contactType Contact type
	 * @param registrationState Three possible values : online/offline/unknown
	 */
	public void setContactCapabilities(ContactId contact, Capabilities capabilities, RcsStatus contactType, RegistrationState registrationState) {
		// Get the current information on this contact 
		ContactInfo oldInfo = getContactInfo(contact);
		ContactInfo newInfo = new ContactInfo(oldInfo);
		
		// Set the contact type 
		newInfo.setRcsStatus(contactType);
		
		// Set the registration state
		newInfo.setRegistrationState(registrationState);

		// Modify the capabilities regarding the registration state		
		boolean isRegistered = RegistrationState.ONLINE.equals(registrationState);
		// Cs Video
		capabilities.setCsVideoSupport(capabilities.isCsVideoSupported() && isRegistered);

		// File transfer. This capability is enabled:
		// - if the capability is present and the contact is registered
		// - if the FT S&F is enabled and the contact is RCS capable		
		capabilities.setFileTransferSupport((capabilities.isFileTransferSupported() && isRegistered) ||
				(RcsSettings.getInstance().isFileTransferStoreForwardSupported() && newInfo.isRcsContact()));
		
		// Image sharing
		capabilities.setImageSharingSupport(capabilities.isImageSharingSupported() && isRegistered);

		// IM session
		// This capability is enabled:
		// - if the capability is present and the contact is registered
		// - if the IM store&forward is enabled and the contact is RCS capable
		capabilities.setImSessionSupport((capabilities.isImSessionSupported() && isRegistered) 
				|| (RcsSettings.getInstance().isImAlwaysOn() && newInfo.isRcsContact()));
		
		// IM session. This capability is enabled:
		// - if the capability is present and the contact is registered
		// - if the IM S&F is enabled and the contact is RCS capable
		// - if the IM store&forward is enabled and the contact is RCS capable
		capabilities.setImSessionSupport((capabilities.isImSessionSupported() && isRegistered) 
				|| (RcsSettings.getInstance().isImAlwaysOn() && newInfo.isRcsContact()));
		
		// Video sharing
		capabilities.setVideoSharingSupport(capabilities.isVideoSharingSupported() && isRegistered);
		
		// Geolocation push
		capabilities.setGeolocationPushSupport(capabilities.isGeolocationPushSupported() && isRegistered);

		// FT thumbnail
		capabilities.setFileTransferThumbnailSupport(capabilities.isFileTransferThumbnailSupported() && isRegistered);

		// FT HTTP
		capabilities.setFileTransferHttpSupport(capabilities.isFileTransferHttpSupported() && isRegistered);
		
		// FT S&F
		capabilities.setFileTransferStoreForwardSupport((capabilities.isFileTransferStoreForwardSupported() && isRegistered)||
				(RcsSettings.getInstance().isFtAlwaysOn() && newInfo.isRcsContact()));

		// Group chat S&F
		capabilities.setGroupChatStoreForwardSupport(capabilities.isGroupChatStoreForwardSupported() && isRegistered);
		
		// IP voice call
		capabilities.setIPVoiceCallSupport(capabilities.isIPVoiceCallSupported() && isRegistered);
		
		// IP video call
		capabilities.setIPVideoCallSupport(capabilities.isIPVideoCallSupported() && isRegistered);
		
		// Add the capabilities
		newInfo.setCapabilities(capabilities);

		// Save the modifications
		try {
			setContactInfo(newInfo, oldInfo);
		} catch (ContactsManagerException e) {
			if (logger.isActivated()){
				logger.error("Could not save the contact modifications",e);
			}
		}
	}
	
	/**
	 * Set contact capabilities
	 * 
	 * @param contact Contact Id
	 * @param caps Capabilities
	 */
	public void setContactCapabilities(ContactId contact, Capabilities caps) {
		// Check if contact exists in RAB
		if (!isContactIdAssociatedWithContactInRichAddressBook(contact)) {
			return;
		}
		// contact exists in RAB : we can update it
		RcsStatus rcsStatus = getContactStatus(contact);
		boolean isRcsContact = (!RcsStatus.NO_INFO.equals(rcsStatus) && !RcsStatus.NOT_RCS.equals(rcsStatus));

		RegistrationState registration = getRegistrationState(contact);
		boolean isRegistered = RegistrationState.ONLINE.equals(registration);

		// Update cache
		mCacheCapabilities.put(contact, caps);
		
		ContentValues values = new ContentValues();
		
		caps.setCsVideoSupport(caps.isCsVideoSupported() && isRegistered);
		int supported = setCapabilityToColumn(caps.isCsVideoSupported());
		values.put(RichAddressBookData.KEY_CAPABILITY_CS_VIDEO, supported);
		
		caps.setFileTransferSupport(caps.isFileTransferSupported() && isRegistered);
		supported = setCapabilityToColumn(caps.isFileTransferSupported());
		values.put(RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER, supported);
		
		caps.setImageSharingSupport(caps.isImageSharingSupported() && isRegistered);
		supported = setCapabilityToColumn(caps.isImageSharingSupported());
		values.put(RichAddressBookData.KEY_CAPABILITY_IMAGE_SHARING, supported);
		
		caps.setImSessionSupport((caps.isImSessionSupported() && isRegistered)
				|| (RcsSettings.getInstance().isImAlwaysOn() && isRcsContact));
		supported = setCapabilityToColumn(caps.isImSessionSupported());
		values.put(RichAddressBookData.KEY_CAPABILITY_IM_SESSION, supported);
		
		caps.setPresenceDiscoverySupport(caps.isPresenceDiscoverySupported() && isRegistered);
		supported = setCapabilityToColumn(caps.isPresenceDiscoverySupported());
		values.put(RichAddressBookData.KEY_CAPABILITY_PRESENCE_DISCOVERY, supported);
		
		caps.setSocialPresenceSupport(caps.isSocialPresenceSupported() && isRegistered);
		supported = setCapabilityToColumn(caps.isSocialPresenceSupported());
		values.put(RichAddressBookData.KEY_CAPABILITY_SOCIAL_PRESENCE, supported);
		
		caps.setVideoSharingSupport(caps.isVideoSharingSupported() && isRegistered);
		supported = setCapabilityToColumn(caps.isVideoSharingSupported());
		values.put(RichAddressBookData.KEY_CAPABILITY_VIDEO_SHARING, supported);
		
		caps.setGeolocationPushSupport(caps.isGeolocationPushSupported() && isRegistered);
		supported = setCapabilityToColumn(caps.isGeolocationPushSupported());
		values.put(RichAddressBookData.KEY_CAPABILITY_GEOLOCATION_PUSH, supported);
		
		caps.setFileTransferHttpSupport(caps.isFileTransferHttpSupported() && isRegistered);
		supported = setCapabilityToColumn(caps.isFileTransferHttpSupported());
		values.put(RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_HTTP, supported);
		
		caps.setFileTransferThumbnailSupport(caps.isFileTransferThumbnailSupported() && isRegistered);
		supported = setCapabilityToColumn(caps.isFileTransferThumbnailSupported());
		values.put(RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_THUMBNAIL, supported);
		
		caps.setIPVoiceCallSupport(caps.isIPVoiceCallSupported() && isRegistered);
		supported = setCapabilityToColumn(caps.isIPVoiceCallSupported());
		values.put(RichAddressBookData.KEY_CAPABILITY_IP_VOICE_CALL, supported);
		
		caps.setIPVideoCallSupport(caps.isIPVideoCallSupported() && isRegistered);
		supported = setCapabilityToColumn(caps.isIPVideoCallSupported());
		values.put(RichAddressBookData.KEY_CAPABILITY_IP_VIDEO_CALL, supported);
		
		caps.setFileTransferStoreForwardSupport((caps.isFileTransferStoreForwardSupported() && isRegistered)
				|| (RcsSettings.getInstance().isFtAlwaysOn() && isRcsContact));
		supported = setCapabilityToColumn(caps.isFileTransferStoreForwardSupported());
		values.put(RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_SF, supported);
		
		caps.setSipAutomata(caps.isSipAutomata() && isRegistered);
		supported = setCapabilityToColumn(caps.isSipAutomata());
		values.put(RichAddressBookData.KEY_AUTOMATA, supported);
		
		caps.setGroupChatStoreForwardSupport(caps.isGroupChatStoreForwardSupported() && isRegistered);
		supported = setCapabilityToColumn(caps.isGroupChatStoreForwardSupported());
		values.put(RichAddressBookData.KEY_CAPABILITY_GROUP_CHAT_SF, supported);
		
		String extensions = ServiceExtensionManager.getInstance().getExtensions(caps.getSupportedExtensions());
		// Save the capabilities extensions
		values.put(RichAddressBookData.KEY_CAPABILITY_EXTENSIONS, extensions);
		
		// Save capabilities timestamp
		values.put(RichAddressBookData.KEY_CAPABILITY_TIME_LAST_RQST, caps.getTimestampOfLastRequest());
		values.put(RichAddressBookData.KEY_CAPABILITY_TIME_LAST_REFRESH, caps.getTimestampOfLastRefresh());
		
		Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
		mResolver.update(uri, values, null, null);
	}
	
	/**
	 * Get the RCS status for contact
	 * @param contact
	 * @return status
	 */
	private RcsStatus getContactStatus(ContactId contact) {
		Cursor cur = null;
		try {
			Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
			cur = mResolver.query(uri, PROJECTION_RCS_STATUS, null, null, null);
			if (cur.moveToFirst()) {
				// Get the RCS status
				int rcsStatus = cur.getInt(cur.getColumnIndex(RichAddressBookData.KEY_RCS_STATUS));
				return RcsStatus.valueOf(rcsStatus);
			}
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Internal exception", e);
			}
		} finally {
			if (cur != null) {
				cur.close();
			}
		}
		return RcsStatus.NO_INFO;
	}
	
	/**
	 * Get the registration state for contact
	 * @param contact
	 * @return registration state
	 */
	private RegistrationState getRegistrationState(ContactId contact) {
		Cursor cur = null;
		try {
			Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
			cur = mResolver.query(uri, PROJECTION_REGISTRATION_STATE, null, null, null);
			if (cur.moveToFirst()) {
				// Get the RCS status
				int registrationState = cur.getInt(cur.getColumnIndex(RichAddressBookData.KEY_REGISTRATION_STATE));
				return RegistrationState.valueOf(registrationState);
			}
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Internal exception", e);
			}
		} finally {
			if (cur != null) {
				cur.close();
			}
		}
		return RegistrationState.UNKNOWN;
	}
	
	/**
	 * Get contact capabilities
	 * <br>If contact has never been enriched with capability, returns null
	 * 
	 * @param contact Contact Id
	 * @return capabilities
	 */
	public Capabilities getContactCapabilities(ContactId contact) {
		if (RcsStatus.NO_INFO.equals(getContactStatus(contact))) {
			return null;
		}
		// Try first to read from cache
		Capabilities caps = mCacheCapabilities.get(contact);
		if (caps != null) {
			return caps;
		}
		// Not in cache then read from provider
		caps = new Capabilities();
		Cursor cur = null;
		try {
			Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
			cur = mResolver.query(uri, PROJECTION_RABP_CAPABILITIES, null, null, null);
			if (cur.moveToFirst()) {
				// Get the capabilities infos
				caps.setCsVideoSupport(getCapabilityFromColumn(cur, RichAddressBookData.KEY_CAPABILITY_CS_VIDEO));
				caps.setFileTransferSupport(getCapabilityFromColumn(cur, RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER));
				caps.setImageSharingSupport(getCapabilityFromColumn(cur, RichAddressBookData.KEY_CAPABILITY_IMAGE_SHARING));
				caps.setImSessionSupport(getCapabilityFromColumn(cur, RichAddressBookData.KEY_CAPABILITY_IM_SESSION));
				caps.setPresenceDiscoverySupport(getCapabilityFromColumn(cur,
						RichAddressBookData.KEY_CAPABILITY_PRESENCE_DISCOVERY));
				caps.setSocialPresenceSupport(getCapabilityFromColumn(cur,
						RichAddressBookData.KEY_CAPABILITY_SOCIAL_PRESENCE));
				caps.setGeolocationPushSupport(getCapabilityFromColumn(cur,
						RichAddressBookData.KEY_CAPABILITY_GEOLOCATION_PUSH));
				caps.setVideoSharingSupport(getCapabilityFromColumn(cur, RichAddressBookData.KEY_CAPABILITY_VIDEO_SHARING));
				caps.setFileTransferThumbnailSupport(getCapabilityFromColumn(cur,
						RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_THUMBNAIL));
				caps.setFileTransferHttpSupport(getCapabilityFromColumn(cur,
						RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_HTTP));
				caps.setIPVoiceCallSupport(getCapabilityFromColumn(cur, RichAddressBookData.KEY_CAPABILITY_IP_VOICE_CALL));
				caps.setIPVideoCallSupport(getCapabilityFromColumn(cur, RichAddressBookData.KEY_CAPABILITY_IP_VIDEO_CALL));
				caps.setFileTransferStoreForwardSupport(getCapabilityFromColumn(cur,
						RichAddressBookData.KEY_CAPABILITY_FILE_TRANSFER_SF));
				caps.setGroupChatStoreForwardSupport(getCapabilityFromColumn(cur,
						RichAddressBookData.KEY_CAPABILITY_GROUP_CHAT_SF));
				caps.setSipAutomata(getCapabilityFromColumn(cur, RichAddressBookData.KEY_AUTOMATA));

				// Set RCS extensions capability
				caps.setSupportedExtensions(ServiceExtensionManager.getInstance().getExtensions(
						cur.getString(cur.getColumnIndex(RichAddressBookData.KEY_CAPABILITY_EXTENSIONS))));

				// Set time of last request
				caps.setTimestampOfLastRequest(cur.getLong(cur
						.getColumnIndex(RichAddressBookData.KEY_CAPABILITY_TIME_LAST_RQST)));
				// Set time of last refresh
				caps.setTimestampOfLastRefresh(cur.getLong(cur
						.getColumnIndex(RichAddressBookData.KEY_CAPABILITY_TIME_LAST_REFRESH)));
				// Insert capabilities into cache
				mCacheCapabilities.put(contact, caps);
			}
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Internal exception", e);
			}
			// remove entry from cache
			mCacheCapabilities.remove(contact);
		} finally {
			if (cur != null) {
				cur.close();
			}
		}
		return caps;
	}
	
	/**
	 * Update time of last capabilities request for contact
	 * 
	 * @param contact
	 *            Contact Id
	 */
	public void updateCapabilitiesTimeLastRequest(ContactId contact) {
		if (logger.isActivated()) {
			logger.debug("Update time of last capabilities request for " + contact);
		}
		Capabilities capabilities = getContactCapabilities(contact);
		if (capabilities == null) {
			return;
		}
		long now = System.currentTimeMillis();
		// Update the cache
		capabilities.setTimestampOfLastRequest(now);
		ContentValues values = new ContentValues();
		values.put(RichAddressBookData.KEY_CAPABILITY_TIME_LAST_RQST, now);
		Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
		mResolver.update(uri, values, null, null);
	}

    /**
     * Utility method to create new "RCS" raw contact, that aggregates with other raw contact
     *
     * @param contact info for the RCS raw contact
     * @param id of the raw contact we want to aggregate the RCS infos to
     * @return the RCS rawContactId concerning this newly created contact
     */
    public long createRcsContact(final ContactInfo info, final long rawContactId) {
        if (logger.isActivated()){
        	logger.debug("Creating new RCS rawcontact for "+info.getContact()+" to be associated to rawContactId "+rawContactId);
        }
        
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

        //Create rawcontact for RCS
        int rawContactRefIms = ops.size();
        ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
        		 .withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED)
                 .withValue(RawContacts.ACCOUNT_TYPE, AuthenticationService.ACCOUNT_MANAGER_TYPE)
                 .withValue(RawContacts.ACCOUNT_NAME, ctx.getString(R.string.rcs_core_account_username))
                 .build());

        // Insert number
        ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                 .withValueBackReference(Data.RAW_CONTACT_ID, rawContactRefIms)
                 .withValue(Data.MIMETYPE, MIMETYPE_NUMBER)
                 .withValue(Data.DATA1, info.getContact().toString())
                 .build());
        
        // Create RCS status row
        ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactRefIms)
                .withValue(Data.MIMETYPE, ContactsManager.MIMETYPE_RCS_STATUS)
                .withValue(Data.DATA1, info.getContact().toString())
                .withValue(Data.DATA2, info.getRcsStatus().toInt())
                .build());
        
        // Insert capabilities if present
        Capabilities capabilities = info.getCapabilities();
        
        // File transfer
        if (capabilities.isFileTransferSupported()) {
            ops.add(createMimeTypeForContact(rawContactRefIms, info.getContact(), MIMETYPE_CAPABILITY_FILE_TRANSFER));
        }
        // Image sharing
        if (capabilities.isImageSharingSupported()) {
            ops.add(createMimeTypeForContact(rawContactRefIms, info.getContact(), MIMETYPE_CAPABILITY_IMAGE_SHARING));
        }
        // IM session
        if (capabilities.isImSessionSupported()) {
            ops.add(createMimeTypeForContact(rawContactRefIms, info.getContact(), MIMETYPE_CAPABILITY_IM_SESSION));
        }
        // Video sharing
        if (capabilities.isVideoSharingSupported()) {
            ops.add(createMimeTypeForContact(rawContactRefIms, info.getContact(), MIMETYPE_CAPABILITY_VIDEO_SHARING));
        }
        // IP Voice call
        if (capabilities.isIPVoiceCallSupported()) {
            ops.add(createMimeTypeForContact(rawContactRefIms, info.getContact(), MIMETYPE_CAPABILITY_IP_VOICE_CALL));
        }
        // IP Video call
        if (capabilities.isIPVideoCallSupported()) {
            ops.add(createMimeTypeForContact(rawContactRefIms, info.getContact(), MIMETYPE_CAPABILITY_IP_VIDEO_CALL));
        }
        // Geolocation push
        if (capabilities.isGeolocationPushSupported()) {
            ops.add(createMimeTypeForContact(rawContactRefIms, info.getContact(), MIMETYPE_CAPABILITY_GEOLOCATION_PUSH));
        }
        // Insert extensions
		ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
				.withValueBackReference(Data.RAW_CONTACT_ID, rawContactRefIms)
				.withValue(Data.MIMETYPE, MIMETYPE_CAPABILITY_EXTENSIONS)
				.withValue(Data.DATA1, info.getContact().toString())
				.withValue(Data.DATA2, ServiceExtensionManager.getInstance().getExtensions(info.getCapabilities().getSupportedExtensions()))
				.withValue(Data.DATA3, info.getContact().toString())
				.build());

    	// Insert registration status
    	ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
    			.withValueBackReference(Data.RAW_CONTACT_ID, rawContactRefIms)
    			.withValue(Data.MIMETYPE, MIMETYPE_REGISTRATION_STATE)
    			.withValue(Data.DATA1, info.getContact().toString())
    			.withValue(Data.DATA2, info.getRegistrationState().toInt())
    			.build());
    	
        // Create the RCS raw contact and get its id        
        long rcsRawContactId = INVALID_ID;
        try {
        	ContentProviderResult[] results;
        	results = mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
        	rcsRawContactId =  ContentUris.parseId(results[rawContactRefIms].uri);
        } catch (RemoteException e) {
        } catch (OperationApplicationException e) {
        	return INVALID_ID;
        }

        // Aggregate the newly RCS raw contact and the raw contact that has the phone number
        ops.clear();
        ops.add(ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
        		.withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER)
        		.withValue(AggregationExceptions.RAW_CONTACT_ID1, rcsRawContactId)
        		.withValue(AggregationExceptions.RAW_CONTACT_ID2, rawContactId).build());

        try {
        	mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
        	// Add to exception provider
        	ContentValues values = new ContentValues();
        	values.put(AggregationData.KEY_RAW_CONTACT_ID, rawContactId);
        	values.put(AggregationData.KEY_RCS_RAW_CONTACT_ID, rcsRawContactId);
        	values.put(AggregationData.KEY_RCS_NUMBER, info.getContact().toString());
        	mResolver.insert(AggregationData.CONTENT_URI, values);
        } catch (RemoteException e) {
        	if (logger.isActivated()){
        		logger.debug("Remote exception => "+e);
        	}
        	return INVALID_ID;
        } catch (OperationApplicationException e) {
        	if (logger.isActivated()){
        		logger.debug("Operation exception => "+e);
        	}
        	return INVALID_ID;
        }

        return rcsRawContactId;
    }

    /**
     * Converts the specified bitmap to a byte array.
     *
     * @param bitmap the Bitmap to convert
     * @return the bitmap as bytes, null if converting fails.
     */
    private byte[] convertBitmapToBytes(final Bitmap bitmap) {
        byte[] iconData = null;
        int size = bitmap.getRowBytes() * bitmap.getHeight();

        ByteArrayOutputStream out = new ByteArrayOutputStream(size);
        try {
            if (bitmap.compress(Bitmap.CompressFormat.PNG, 0 /* quality ignored for PNG */, out)) {
                out.close();
                iconData = out.toByteArray();
            } else {
                out.close();
                if (logger.isActivated()){
                	logger.debug("Unable to convert bitmap, compression failed");
                }
            }
        } catch (IOException e) {
        	if (logger.isActivated()){
        		logger.error("Unable to convert bitmap", e);
        	}
            iconData = null;
        }

        return iconData;
    }
    
    /**
     * Utility method to create the "Me" raw contact.
     *
     * @param context The application context.
     * @return the rawContactId of the newly created contact
     */
    public long createMyContact() {
    	RcsSettings.createInstance(ctx);
		if (!RcsSettings.getInstance().isSocialPresenceSupported()){
			return INVALID_ID;
		}

        // Check if IMS account exists before continue
        AccountManager am = AccountManager.get(ctx);
        if (am.getAccountsByType(AuthenticationService.ACCOUNT_MANAGER_TYPE).length == 0) {
        	if (logger.isActivated()){
        		logger.error("Could not create \"Me\" contact, no RCS account found");
        	}
            throw new IllegalStateException("No RCS account found");
        }

        // Check if RCS raw contact for "Me" does not already exist
        long imsRawContactId = getRawContactIdForMe();
        
        if (imsRawContactId != INVALID_ID) {
        	if (logger.isActivated()){
        		logger.error("\"Me\" contact already exists, no need to recreate");
        	}
        }else{
        	if (logger.isActivated()){
        		logger.error("\"Me\" contact does not already exists, creating it");
        	}
        	
            ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

            //Create rawcontact for RCS
            int rawContactRefIms = ops.size();
            ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                     .withValue(RawContacts.ACCOUNT_TYPE, AuthenticationService.ACCOUNT_MANAGER_TYPE)
                     .withValue(RawContacts.ACCOUNT_NAME, ctx.getString(R.string.rcs_core_account_username))
                     .withValue(RawContacts.SOURCE_ID, MYSELF)                     
                     .withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED)
                     .build());

            // Set name
            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, rawContactRefIms)
                    .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, ctx.getString(R.string.rcs_core_my_profile))
                    .build());
            
            try {
                ContentProviderResult[] results;
                results = mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
                imsRawContactId = ContentUris.parseId(results[rawContactRefIms].uri);
            } catch (RemoteException e) {
            	imsRawContactId = INVALID_ID;
            } catch (OperationApplicationException e) {
            	imsRawContactId =  INVALID_ID;
            }
            
            ops.clear();
            
            // Set default free text to null and availability to online
			List<ContentProviderOperation> registrationStateOps = modifyContactRegistrationStateForMyself(imsRawContactId,
					RegistrationState.ONLINE, RegistrationState.UNKNOWN, "", "");
			for (ContentProviderOperation registrationStateOp : registrationStateOps) {
				if (registrationStateOp != null) {
					ops.add(registrationStateOp);
				}
			}
    		
            try {
            	mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
            } catch (RemoteException e) {
            	imsRawContactId = INVALID_ID;
            } catch (OperationApplicationException e) {
            	imsRawContactId =  INVALID_ID;
            }
        }

        return imsRawContactId;
    }

    /**
     * Utility to find the rawContactIds for a specific phone number.
     *
     * @param contact the contact ID to search for
     * @return list of contact Ids
     */
    private List<Long> getRawContactIdsFromPhoneNumber(ContactId contact) {
        List<Long> rawContactsIds = new ArrayList<Long>(); 
    	String[] projection = { Data.RAW_CONTACT_ID };
        String selection = Data.MIMETYPE + "=? AND PHONE_NUMBERS_EQUAL(" + Phone.NUMBER + ", ?)";
        String[] selectionArgs = { Phone.CONTENT_ITEM_TYPE, contact.toString() };
        String sortOrder = Data.RAW_CONTACT_ID;

		// Starting LOOSE equal
		Cursor cur = null;
		try {
			cur = mResolver.query(Data.CONTENT_URI, projection, selection, selectionArgs, sortOrder);
			while (cur.moveToNext()) {
				long rawContactId = cur.getLong(cur.getColumnIndex(Data.RAW_CONTACT_ID));
				if (!rawContactsIds.contains(rawContactId) && (!isSimAccount(rawContactId) || (Build.VERSION.SDK_INT > 10))) { // Build.VERSION_CODES.GINGERBREAD_MR1
					// We exclude the SIM only contacts, as they cannot be aggregated to a RCS raw contact
					// only if OS version if gingebread or fewer
					rawContactsIds.add(rawContactId);
				}
			}
		} catch (Exception e) {
		} finally {
			if (cur != null) {
				cur.close();
			}
		}

        /* No match found using LOOSE equals, starting STRICT equals.
         *
         * This is done because of that the PHONE_NUMBERS_EQUAL function in Android
         * doesn't always return true when doing loose lookup of a phone number
         * against itself
         */
        String selectionStrict = Data.MIMETYPE + "=? AND (NOT PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
                + ", ?) AND PHONE_NUMBERS_EQUAL(" + Phone.NUMBER + ", ?, 1))";
        String[] selectionArgsStrict = { Phone.CONTENT_ITEM_TYPE, contact.toString(), contact.toString() };
        cur = null;
		try {
			cur = mResolver.query(Data.CONTENT_URI, projection, selectionStrict, selectionArgsStrict, sortOrder);
			while (cur.moveToNext()) {
				long rawContactId = cur.getLong(cur.getColumnIndex(Data.RAW_CONTACT_ID));
				if (!rawContactsIds.contains(rawContactId) && (!isSimAccount(rawContactId) || (Build.VERSION.SDK_INT > 10))) { 
					// Build.VERSION_CODES.GINGERBREAD_MR1
					// We exclude the SIM only contacts, as they cannot be aggregated to a RCS raw contact
					// only if OS version if gingerbread or fewer
					rawContactsIds.add(rawContactId);
				}
			}
		} catch (Exception e) {
		} finally {
			if (cur != null) {
				cur.close();
			}
		}
        
        return rawContactsIds;
    }
    
    /**
     * Utility to get the RCS rawContact associated to a raw contact
     *
     * @param rawContactId the id of the rawContact
     * @param contact The contact ID
     * @return the id of the associated RCS rawContact
     */
	public long getAssociatedRcsRawContact(final long rawContactId, final ContactId contact) {
		Cursor cursor = null;
		try {
			cursor = mResolver.query(
					AggregationData.CONTENT_URI,
					new String[] { AggregationData.KEY_RCS_RAW_CONTACT_ID },
					AggregationData.KEY_RCS_NUMBER + "=?" + " AND " + AggregationData.KEY_RAW_CONTACT_ID + "=?",
					new String[] { contact.toString(), String.valueOf(rawContactId) }, null);
			if (cursor.moveToFirst()) {
				return cursor.getLong(cursor.getColumnIndex( AggregationData.KEY_RCS_RAW_CONTACT_ID));
			}
		} catch (Exception e) {
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return INVALID_ID;
	}

	/**
	 * Utility to check if a phone number is associated to an entry in the rich
	 * address book provider
	 *
	 * @param contact The contact ID
	 * @return true if contact has an entry in the rich address book provider,
	 *         else false
	 */
	public boolean isContactIdAssociatedWithContactInRichAddressBook(final ContactId contact) {
		Cursor cursor = null;
		try {
			Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
			cursor = mResolver.query(uri, PROJECTION_RABP, null, null, null);
			return cursor.moveToFirst();
		} catch (Exception e) {
			return false;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

    /**
     * Utility method to check if a raw contact is only associated to a SIM account
     *
     * @param contact the contact Identifier
     * @return true if the raw contact is only associated to a SIM account, else false
     */
    public boolean isOnlySimAssociated(final ContactId contact) {
		List<Long> rawContactIds = getRawContactIdsFromPhoneNumber(contact);
		for (int i = 0; i < rawContactIds.size(); i++) {
			Cursor rawCur = null;
			try {
				rawCur = mResolver.query(
						RawContacts.CONTENT_URI,
						new String[] { RawContacts._ID },
						"(" + RawContacts.ACCOUNT_TYPE + " IS NULL OR " + RawContacts.ACCOUNT_TYPE + " <> \'" + SIM_ACCOUNT_NAME
								+ "\') AND " + RawContacts._ID + "= " + Long.toString(rawContactIds.get(i)), null, null);
				if (rawCur.getCount() > 0) {
					return false;
				}
			} catch (Exception e) {
			} finally {
				if (rawCur != null) {
					rawCur.close();
				}
			}
		}
		return true;
    }
    
    /**
     * Utility method to check if a raw contact id is a SIM account
     * 
     * @param rawContactId
     * @return
     */
	public boolean isSimAccount(final long rawContactId) {
		Cursor rawCur = null;
		try {
			rawCur = mResolver.query(
					RawContacts.CONTENT_URI,
					new String[] { RawContacts._ID },
					RawContacts.ACCOUNT_TYPE + "= \'" + SIM_ACCOUNT_NAME + "\' AND " + RawContacts._ID + "= "
							+ Long.toString(rawContactId), null, null);
			return (rawCur.getCount() > 0);
		} catch (Exception e) {
			return false;
		} finally {
			if (rawCur != null) {
				rawCur.close();
			}
		}
	}
       
    /**
     * Utility to set the photo icon attribute on a RCS contact.
     *
     * @param rawContactId RCS rawcontact
     * @param photoIcon The photoIcon
     * @param makeSuperPrimary whether or not to set the super primary flag
     * @return 
     */
    private List<ContentProviderOperation> setContactPhoto(Long rawContactId, PhotoIcon photoIcon, boolean makeSuperPrimary) {

    	List<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
    	
    	// Get the photo data id
    	String[] projection = { Data._ID };
    	String selection = Data.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?";
    	String[] selectionArgs = { Long.toString(rawContactId), Photo.CONTENT_ITEM_TYPE };
    	String sortOrder = Data._ID + " DESC";

    	Cursor cur = mResolver.query(Data.CONTENT_URI, 
    			projection, 
    			selection,
    			selectionArgs, 
    			sortOrder);
    	if (cur == null) {
    		return ops;
    	}

    	byte[] iconData = null;
    	if (photoIcon!=null){
    		iconData = photoIcon.getContent();	
    	}         

    	// Insert default avatar if icon is null and it is not for myself
    	if (iconData == null
    			&& rawContactId != getRawContactIdForMe()) {
    		Bitmap rcsAvatar = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.rcs_core_default_portrait_icon);
    		iconData = convertBitmapToBytes(rcsAvatar);
    	}

    	try {
    		long dataId = INVALID_ID;
    		if (iconData == null) {
    			// May happen only for myself
    			// Remove photoIcon if no data
    			if (cur.moveToNext()) {
    				dataId = cur.getLong(cur.getColumnIndex(Data._ID));
    				// Add delete operation
    				ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI)
	        			.withSelection(Data._ID+"=?", new String[]{String.valueOf(dataId)})
	        			.build());
    			}
    		} else {
    			ContentValues values = new ContentValues();
    			values.put(Data.RAW_CONTACT_ID, rawContactId);
    			values.put(Photo.PHOTO, iconData);
    			values.put(Data.IS_PRIMARY, 1);
    			if (makeSuperPrimary) {
    				values.put(Data.IS_SUPER_PRIMARY, 1);
    			}
    			if (cur.moveToNext()) {
    				// We already had an icon, update it
    				dataId = cur.getLong(cur.getColumnIndex(Data._ID));
    				ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
    	        			.withSelection(Data._ID+"=?", new String[]{String.valueOf(dataId)})
    	        			.withValues(values)
    	        			.build());
    			} else {
    				// We did not have an icon, insert a new one
    				ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
    	        			.withValues(values)
    	        			.build());
    			}

    			values.clear();

    			// Set etag
    			values.put(Data.RAW_CONTACT_ID, rawContactId);
    			String etag = null;
    			if (photoIcon!=null){
    				etag = photoIcon.getEtag();
    			}
    			values.put(Data.DATA2, etag);

    			String[] projection2 = { Data._ID, Data.RAW_CONTACT_ID, Data.MIMETYPE };
    			String selection2 = Data.RAW_CONTACT_ID + "=?";
    			String[] selectionArgs2 = { Long.toString(rawContactId) };

				Cursor cur2 = null;
				try {
					cur2 = mResolver.query(Data.CONTENT_URI, projection2, selection2, selectionArgs2, null);
					if (cur2.moveToNext()) {
						dataId = cur2.getLong(cur.getColumnIndex(Data._ID));
						// We already had an etag, update it
						dataId = cur.getLong(cur.getColumnIndex(Data._ID));
						ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
								.withSelection(Data._ID + "=?", new String[] { String.valueOf(dataId) }).withValues(values).build());
					} else {
						// Insert etag
						ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI).withValues(values).build());
					}
				} catch (Exception e) {
				} finally {
					if (cur2 != null) {
						cur2.close();
					}
				}
    		}
    	} finally {
    		cur.close();
    	}
    	return ops;
    }

    /**
     * Get the raw contact id of the "Me" contact.
     *
     * @return rawContactId
     */
	private long getRawContactIdForMe() {
		String[] projection = { RawContacts._ID };
		String selection = RawContacts.ACCOUNT_TYPE + "=? AND " + RawContacts.SOURCE_ID + "=?";
		String[] selectionArgs = { AuthenticationService.ACCOUNT_MANAGER_TYPE, MYSELF };
		Cursor cur = null;
		try {
			cur = mResolver.query(RawContacts.CONTENT_URI, projection, selection, selectionArgs, null);
			if (cur.moveToNext()) {
				return cur.getLong(cur.getColumnIndexOrThrow(RawContacts._ID));
			}
		} catch (Exception e) {
		} finally {
			if (cur != null) {
				cur.close();
			}
		}
		return INVALID_ID;
	}
    
    /**
     * Get whether the "IM" feature is enabled or not for the contact
     * 
     * @param contact
     * @return flag indicating if IM sessions with the contact are enabled or not
     */
    public boolean isImBlockedForContact(ContactId contact){
		Cursor cursor = null;
		try {
			Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
			cursor = mResolver.query(uri, PROJECTION_RABP, SELECTION_RAPB_IM_BLOCKED, null, null);
			return cursor.moveToFirst();
		} catch (Exception e) {
			return false;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
    }
    
    /**
     * Get whether the "FT" feature is enabled or not for the contact
     * 
     * @param contact
     * @return flag indicating if FT sessions with the contact are enabled or not
     */
	public boolean isFtBlockedForContact(ContactId contact) {
		Cursor cursor = null;
		try {
			Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
			cursor = mResolver.query(uri, PROJECTION_RABP, SELECTION_RABP_FT_BLOCKED, null, null);
			return cursor.moveToFirst();
		} catch (Exception e) {
			return false;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}
    
    /**
     * Utility to create a ContactInfo object from a cursor containing data
     * 
     * @param cursor
     * @return contactInfo
     */
	private ContactInfo getContactInfoFromCursor(Cursor cursor) {
		ContactInfo contactInfo = new ContactInfo();
		PresenceInfo presenceInfo = new PresenceInfo();
		Capabilities capabilities = new Capabilities();

		while (cursor.moveToNext()) {
			String mimeTypeStr = cursor.getString(cursor.getColumnIndex(Data.MIMETYPE));
			try {
				// Convert mime type string to enumerated
				MimeType mimeType = MimeType.valueOf(mimeTypeStr);
				switch (mimeType) {
				case CAPABILITY_IMAGE_SHARING:
					// Set capability image sharing
					capabilities.setImageSharingSupport(true);
					break;
				case CAPABILITY_VIDEO_SHARING:
					// Set capability video sharing
					capabilities.setVideoSharingSupport(true);
					break;
				case CAPABILITY_IP_VOICE_CALL:
					// Set capability ip voice call
					capabilities.setIPVoiceCallSupport(true);
					break;
				case CAPABILITY_IP_VIDEO_CALL:
					// Set capability ip video call
					capabilities.setIPVideoCallSupport(true);
					break;
				case CAPABILITY_IM_SESSION:
					// Set capability IM session
					capabilities.setImSessionSupport(true);
					break;
				case CAPABILITY_FILE_TRANSFER:
					// Set capability file transfer
					capabilities.setFileTransferSupport(true);
					break;
				case CAPABILITY_GEOLOCATION_PUSH:
					// Set capability geoloc push
					capabilities.setGeolocationPushSupport(true);
					break;
				case CAPABILITY_EXTENSIONS: {
					// Set RCS extensions capability
					int columnIndex = cursor.getColumnIndex(Data.DATA2);
					if (columnIndex != -1) {
						capabilities.setSupportedExtensions(ServiceExtensionManager.getInstance().getExtensions(
								cursor.getString(columnIndex)));
					}
				}
					break;
				case REGISTRATION_STATE: {
					// Set registration state
					int columnIndex = cursor.getColumnIndex(Data.DATA2);
					if (columnIndex != -1) {
						int registrationState = cursor.getInt(columnIndex);
						contactInfo.setRegistrationState(RegistrationState.valueOf(registrationState));
					}
				}
					break;
				case NUMBER: {
					// Set contact
					int columnIndex = cursor.getColumnIndex(Data.DATA1);
					if (columnIndex != -1) {
						String contact = cursor.getString(columnIndex);
						try {
							contactInfo.setContact(ContactUtils.createContactId(contact));
						} catch (RcsContactFormatException e) {
							if (logger.isActivated()) {
								logger.warn("Cannot parse contact " + contact);
							}
						}
					}
				}
					break;
				default:
					if (logger.isActivated()) {
						logger.warn("Unhandled mimetype " + mimeTypeStr);
					}
					break;
				}
			} catch (Exception e) {
				if (logger.isActivated()) {
					logger.warn("Invalid mimetype " + mimeTypeStr);
				}
			}
		}
		cursor.close();
		contactInfo.setPresenceInfo(presenceInfo);
		contactInfo.setCapabilities(capabilities);

		return contactInfo;
	}
    
    /**
     * Utility to extract data from a raw contact.
     *
     * @param rawContactId the rawContactId
     * @return A cursor containing the requested data.
     */
    private Cursor getRawContactDataCursor(final long rawContactId) {
        String[] projection = {
                Data._ID, 
                Data.MIMETYPE, 
                Data.DATA1, 
                Data.DATA2, 
                Website.URL,
                Photo.PHOTO          
        };

        // Filter the mime types 
        String selection = "(" + Data.RAW_CONTACT_ID + " =?) AND (" 
                + Data.MIMETYPE + "=? OR "
                + Data.MIMETYPE + "=? OR "
                + Data.MIMETYPE + "=? OR "
                + Data.MIMETYPE + "=? OR "
                + Data.MIMETYPE + "=? OR "
                + Data.MIMETYPE + "=? OR "
                + Data.MIMETYPE + "=? OR "
                + Data.MIMETYPE + "=? OR "
                + Data.MIMETYPE + "=? OR "
                + Data.MIMETYPE + "=?)";
        String[] selectionArgs = {
                Long.toString(rawContactId), 
                MIMETYPE_REGISTRATION_STATE,
                MIMETYPE_NUMBER,
                MIMETYPE_CAPABILITY_IMAGE_SHARING,
                MIMETYPE_CAPABILITY_VIDEO_SHARING,
                MIMETYPE_CAPABILITY_IP_VOICE_CALL,
                MIMETYPE_CAPABILITY_IP_VIDEO_CALL,
                MIMETYPE_CAPABILITY_IM_SESSION,
                MIMETYPE_CAPABILITY_FILE_TRANSFER,
                MIMETYPE_CAPABILITY_GEOLOCATION_PUSH,
                MIMETYPE_CAPABILITY_EXTENSIONS
        };

        Cursor cur = mResolver.query(Data.CONTENT_URI, 
        		projection, 
        		selection, 
        		selectionArgs, 
        		null);

        return cur;
    }
    
    /**
     * Update UI strings when device's locale has changed
     */
    public void updateStrings(){
    	
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
    	
    	// Update My profile display name
    	ContentValues values = new ContentValues();
    	values.put(StructuredName.DISPLAY_NAME, ctx.getString(R.string.rcs_core_my_profile));
    	
    	ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
    			.withSelection("(" + Data.RAW_CONTACT_ID + " =?) AND (" + Data.MIMETYPE + "=?)", new String[]{Long.toString(getRawContactIdForMe()), StructuredName.DISPLAY_NAME})
    			.withValues(values)
    			.build());
    	
    	// Update file transfer menu
    	values.clear();
    	values.put(Data.DATA2, getMimeTypeDescription(MIMETYPE_CAPABILITY_FILE_TRANSFER));
    	ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
    			.withSelection(Data.MIMETYPE + "=?", new String[]{MIMETYPE_CAPABILITY_FILE_TRANSFER})
    			.withValues(values)
    			.build());
    	
    	// Update chat menu 
    	values.clear();
    	values.put(Data.DATA2, getMimeTypeDescription(MIMETYPE_CAPABILITY_IM_SESSION));
    	ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
    			.withSelection(Data.MIMETYPE + "=?", new String[]{MIMETYPE_CAPABILITY_IM_SESSION})
    			.withValues(values)
    			.build());

    	// Update image sharing menu 
    	values.clear();
    	values.put(Data.DATA2, getMimeTypeDescription(MIMETYPE_CAPABILITY_IMAGE_SHARING));
    	ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
    			.withSelection(Data.MIMETYPE + "=?", new String[]{MIMETYPE_CAPABILITY_IMAGE_SHARING})
    			.withValues(values)
    			.build());

    	// Update video sharing menu 
    	values.clear();
    	values.put(Data.DATA2, getMimeTypeDescription(MIMETYPE_CAPABILITY_VIDEO_SHARING));
    	ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
    			.withSelection(Data.MIMETYPE + "=?", new String[]{MIMETYPE_CAPABILITY_VIDEO_SHARING})
    			.withValues(values)
    			.build());

    	// Update IP voice call menu 
    	values.clear();
    	values.put(Data.DATA2, getMimeTypeDescription(MIMETYPE_CAPABILITY_IP_VOICE_CALL));
    	ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
    			.withSelection(Data.MIMETYPE + "=?", new String[]{MIMETYPE_CAPABILITY_IP_VOICE_CALL})
    			.withValues(values)
    			.build());

    	// Update IP video call menu 
    	values.clear();
    	values.put(Data.DATA2, getMimeTypeDescription(MIMETYPE_CAPABILITY_IP_VIDEO_CALL));
    	ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
    			.withSelection(Data.MIMETYPE + "=?", new String[]{MIMETYPE_CAPABILITY_IP_VIDEO_CALL})
    			.withValues(values)
    			.build());

    	if (!ops.isEmpty()){
			// Do the actual database modifications
			try {
				mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
			} catch (RemoteException e) {
				if (logger.isActivated()){
					logger.error("Something went wrong when updating the database strings",e);
				}
			} catch (OperationApplicationException e) {
				if (logger.isActivated()){
					logger.error("Something went wrong when updating the database strings",e);
				}
			}
		}
    }

    /**
     * Clean the RCS entries
     *
     * <br>This removes the RCS entries that are associated to numbers not present in the address book anymore
     * <br>This also creates a RCS raw contact for numbers that are present, have RCS raw contact but not on all raw contacts 
     * (typical example: a RCS number is present in the address book and another contact is created using the same number)
     */
    public void cleanRCSEntries() {
        cleanRCSRawContactsInAB();
        cleanEntriesInRichAB();
    }

    /**
     * Clean AB
     */
    private void cleanRCSRawContactsInAB() {
		// Get all RCS raw contacts id
		String[] projection = { Data.RAW_CONTACT_ID, Data.DATA1 };
		String selection = Data.MIMETYPE + "=?";
		String[] selectionArgs = { MIMETYPE_NUMBER };
        // Delete RCS Entry where number is not in the address book anymore
     	ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
		Cursor cursor = null;
		try {
			cursor = mResolver.query(Data.CONTENT_URI, projection, selection, selectionArgs, null);
			while (cursor.moveToNext()) {
				long rawContactId = cursor.getLong(cursor.getColumnIndex(Data.RAW_CONTACT_ID));
				String phoneNumber = cursor.getString(cursor.getColumnIndex(Data.DATA1));
				try {
					ContactId contact = ContactUtils.createContactId(phoneNumber);
					if (getRawContactIdsFromPhoneNumber(contact).isEmpty()) {
						ops.add(ContentProviderOperation.newDelete(RawContacts.CONTENT_URI)
								.withSelection(RawContacts._ID + "=?", new String[] { Long.toString(rawContactId) }).build());
						// Also delete the corresponding entries in the aggregation provider
						mResolver.delete(AggregationData.CONTENT_URI, AggregationData.KEY_RCS_RAW_CONTACT_ID + "=?",
								new String[] { Long.toString(rawContactId) });
					}
				} catch (RcsContactFormatException e) {
					if (logger.isActivated()) {
						logger.warn("Cannot parse contact "+phoneNumber);
					}
				}
			}
		} catch (Exception e) {
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}

		if (!ops.isEmpty()){
			// Do the actual database modifications
			try {
				mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
			} catch (RemoteException e) {
				if (logger.isActivated()){
					logger.error("Something went wrong when updating the database strings",e);
				}
			} catch (OperationApplicationException e) {
				if (logger.isActivated()){
					logger.error("Something went wrong when updating the database strings",e);
				}
			}
		}
    }

    /**
     * Clean EAB
     */
    private void cleanEntriesInRichAB() {
    	// Empty the cache
    	mCacheCapabilities.clear();
		// Get All contact in EAB
		Cursor cursor = null;
		try {
			cursor = mResolver.query(RichAddressBookData.CONTENT_URI, PROJECTION_RABP, null, null, null);

			// Delete EAB Entry where number is not in the address book anymore
			while (cursor.moveToNext()) {
				String phoneNumber = cursor.getString(cursor.getColumnIndex(RichAddressBookData.KEY_CONTACT));
				try {
					ContactId contact = ContactUtils.createContactId(phoneNumber);
					if (getRawContactIdsFromPhoneNumber(contact).isEmpty()) {
						Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, phoneNumber);
						mResolver.delete(uri, null, null);
					}
				} catch (RcsContactFormatException e) {
					if (logger.isActivated()) {
						logger.warn("Cannot parse contact "+phoneNumber);
					}
				}
			}
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Clean entries has failed", e);
			}
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
    }

    /**
     * Delete all RCS entries in databases
     */
    public void deleteRCSEntries() {
        // Delete Aggregation data
    	mResolver.delete(AggregationData.CONTENT_URI, null, null);

    	// Empty the cache
    	mCacheCapabilities.clear();
    	
        // Delete presence data
    	mResolver.delete(RichAddressBookData.CONTENT_URI, null, null);
    }
    
    /**
     * Get boolean capability from database column
     * 
     * @param cursor Cursor
     * @param column Column name
     * @return Boolean capability
     */
    private boolean getCapabilityFromColumn(Cursor cursor, String column) {
    	return (cursor.getInt(cursor.getColumnIndex(column)) == CapabilitiesLog.SUPPORTED);
    }

    /**
     * Set boolean capability to database column
     * 
     * @param capability Boolean capability
     * @return Integer
     */
	private int setCapabilityToColumn(boolean capability) {
		return (capability) ? CapabilitiesLog.SUPPORTED : CapabilitiesLog.NOT_SUPPORTED;
	}
    
	/**
	 * Set the display name into the rich address book provider
	 * 
	 * @param contact Contact ID
	 * @param RCS display name
	 */
	public void setContactDisplayName(ContactId contact, String displayName) {
		if (contact == null) {
			return;
		}
		ContentValues values = new ContentValues();
		values.put(RichAddressBookData.KEY_DISPLAY_NAME, displayName);
		// Check if record exists and if so then update is required
		try {
			String oldDisplayName = getContactDisplayName(contact);
			boolean updateRequired = !StringUtils.equals(oldDisplayName, displayName);
			if (updateRequired) {
				Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
				// Contact already present and display name is new, update
				mResolver.update(uri, values, null, null);
			}
		} catch (IllegalStateException e) {
			// RCS account does not exist : insert it
			values.put(RichAddressBookData.KEY_CONTACT, contact.toString());
			values.put(RichAddressBookData.KEY_TIMESTAMP, System.currentTimeMillis());
			// Contact not present in provider, insert
			mResolver.insert(RichAddressBookData.CONTENT_URI, values);
		}
	}
	
	/**
	 * Get RCS display name for contact
	 * 
	 * @param contact
	 * @return the display name or IllegalStateException if RCS account is not created
	 */
	public String getContactDisplayName(ContactId contact) {
		Cursor cur = null;
		try {
			Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
			cur = mResolver.query(uri, PROJECTION_RABP_DISPLAY_NAME, null, null, null);
			if (cur.moveToFirst()) {
				return cur.getString(cur.getColumnIndex(RichAddressBookData.KEY_DISPLAY_NAME));
			}
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Internal exception", e);
			}
		} finally {
			if (cur != null) {
				cur.close();
			}
		}
		throw new IllegalStateException("No RCS account found");
	}

	/**
	 * Update the time of last capabilities refresh
	 * 
	 * @param contact
	 */
	public void updateCapabilitiesTimeLastRefresh(ContactId contact) {
		if (logger.isActivated()) {
			logger.debug("Update the time of last capabilities refresh for " + contact);
		}
		Capabilities capabilities = getContactCapabilities(contact);
		if (capabilities == null) {
			return;
		}
		long now = System.currentTimeMillis();
		// Update the cache
		capabilities.setTimestampOfLastRefresh(now);
		ContentValues values = new ContentValues();
		values.put(RichAddressBookData.KEY_CAPABILITY_TIME_LAST_REFRESH, now);
		Uri uri = Uri.withAppendedPath(RichAddressBookData.CONTENT_URI, contact.toString());
		mResolver.update(uri, values, null, null);
	}
}

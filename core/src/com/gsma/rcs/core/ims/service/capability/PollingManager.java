/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
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
 ******************************************************************************/

package com.gsma.rcs.core.ims.service.capability;

import java.util.Set;

import com.gsma.rcs.provider.eab.ContactsManager;
import com.gsma.rcs.provider.settings.RcsSettings;
import com.gsma.rcs.utils.PeriodicRefresher;
import com.gsma.rcs.utils.logger.Logger;
import com.gsma.services.rcs.contacts.ContactId;

/**
 * Polling manager which updates capabilities periodically
 * 
 * @author Jean-Marc AUFFRET
 */
public class PollingManager extends PeriodicRefresher {

    /**
     * Capability service
     */
    private final CapabilityService mImsService;

    /**
     * Polling period (in seconds)
     */
    private final int mPollingPeriod;

    /**
     * The logger
     */
    private final static Logger sLogger = Logger.getLogger(PollingManager.class.getSimpleName());

    /**
     * Constructor
     * 
     * @param parent IMS service
     * @param rcsSettings
     */
    public PollingManager(CapabilityService parent, RcsSettings rcsSettings) {
        mImsService = parent;
        mPollingPeriod = rcsSettings.getCapabilityPollingPeriod();
    }

    /**
     * Start polling
     */
    public void start() {
        if (mPollingPeriod == 0) {
            return;
        }
        startTimer(mPollingPeriod, 1);
    }

    /**
     * Stop polling
     */
    public void stop() {
        stopTimer();
    }

    /**
     * Update processing
     */
    public void periodicProcessing() {
        // Make a registration
        if (sLogger.isActivated()) {
            sLogger.info("Execute new capabilities update");
        }

        // Update all contacts capabilities if refresh timeout has not expired
        Set<ContactId> contacts = ContactsManager.getInstance().getAllContacts();
        for (ContactId contact : contacts) {
            requestContactCapabilities(contact);
        }

        // Restart timer
        startTimer(mPollingPeriod, 1);
    }

    /**
     * Request contact capabilities
     * 
     * @param contact Contact identifier
     */
    private void requestContactCapabilities(ContactId contact) {
        if (sLogger.isActivated()) {
            sLogger.debug("Request capabilities for " + contact);
        }

        // Read capabilities from the database
        Capabilities capabilities = ContactsManager.getInstance().getContactCapabilities(contact);
        if (capabilities == null) {
            if (sLogger.isActivated()) {
                sLogger.debug("No capability exist for " + contact);
            }

            // New contact: request capabilities from the network
            mImsService.getOptionsManager().requestCapabilities(contact);
        } else {
            if (sLogger.isActivated()) {
                sLogger.debug("Capabilities exist for " + contact);
            }
            if (isCapabilityRefreshRequired(capabilities.getTimestampOfLastRefresh())) {
                if (sLogger.isActivated()) {
                    sLogger.debug("Capabilities have expired for " + contact);
                }

                // Capabilities are too old: request capabilities from the network
                if (capabilities.isPresenceDiscoverySupported()) {
                    // If contact supports capability discovery via presence, use the selected
                    // discoveryManager
                    mImsService.getAnonymousFetchManager().requestCapabilities(contact);
                } else {
                    // The contact only supports OPTIONS requests
                    mImsService.getOptionsManager().requestCapabilities(contact);
                }
            }
        }
    }

    /**
     * Check if refresh of capability is required
     * 
     * @param timestampOfLastRefresh time of last capability refresh in milliseconds
     * @return true if capability refresh is required
     */
    /* package private */static boolean isCapabilityRefreshRequired(long timestampOfLastRefresh) {
        long now = System.currentTimeMillis();
        // Is current time before last capability refresh ? (may occur if system time has been
        // modified)
        if (now < timestampOfLastRefresh) {
            return true;
        }
        // Is current time after capability expiration time ?
        return (now > (timestampOfLastRefresh + RcsSettings.getInstance()
                .getCapabilityExpiryTimeout() * 1000));
    }
}
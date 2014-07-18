package com.gsma.services.rcs.ft;

import com.gsma.services.rcs.contacts.ContactId;

/**
 * File transfer interface
 */
interface IFileTransfer {

	String getChatId();

	String getTransferId();

	ContactId getRemoteContact();

	String getFileName();

	long getFileSize();

	String getFileType();

	Uri getFileIcon();

	Uri getFile();

	int getState();
	
	int getDirection();
		
	void acceptInvitation();

	void rejectInvitation();

	void abortTransfer();
	
	void pauseTransfer();
	
	void resumeTransfer();
}

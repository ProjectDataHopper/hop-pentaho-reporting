/*
 * Copyright (C) 2026 Project Data Hopper
 *
 * Ported from the historically LGPL-licensed PDI reporting VFS repository bridge.
 * Distributed under LGPL-2.1 with this plugin.
 */

package org.projectdatahopper.hop.pipeline.transforms.pentahoreporting.urlrepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.pentaho.reporting.libraries.repository.ContentIOException;
import org.pentaho.reporting.libraries.repository.ContentItem;
import org.pentaho.reporting.libraries.repository.ContentLocation;

/** File content item wrapping a VFS {@link FileObject}. */
public class FileObjectContentItem extends FileObjectContentEntity implements ContentItem {
  private static final long serialVersionUID = 5080072160607835550L;

  public FileObjectContentItem(ContentLocation parent, FileObject backend) {
    super(parent, backend);
  }

  @Override
  public String getMimeType() throws ContentIOException {
    FileObjectRepository fileRepository = (FileObjectRepository) getRepository();
    return fileRepository.getMimeRegistry().getMimeType(this);
  }

  @Override
  public OutputStream getOutputStream() throws ContentIOException, IOException {
    return getBackend().getContent().getOutputStream();
  }

  @Override
  public InputStream getInputStream() throws ContentIOException, IOException {
    return getBackend().getContent().getInputStream();
  }

  @Override
  public boolean isReadable() {
    try {
      return getBackend().isReadable();
    } catch (FileSystemException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean isWriteable() {
    try {
      return getBackend().isWriteable();
    } catch (FileSystemException e) {
      throw new RuntimeException(e);
    }
  }
}

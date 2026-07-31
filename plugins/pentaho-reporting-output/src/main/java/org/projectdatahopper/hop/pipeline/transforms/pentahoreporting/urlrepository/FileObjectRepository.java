/*
 * Copyright (C) 2026 Project Data Hopper
 *
 * Ported from the historically LGPL-licensed PDI reporting VFS repository bridge.
 * Distributed under LGPL-2.1 with this plugin.
 */

package org.projectdatahopper.hop.pipeline.transforms.pentahoreporting.urlrepository;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.pentaho.reporting.libraries.repository.ContentIOException;
import org.pentaho.reporting.libraries.repository.ContentLocation;
import org.pentaho.reporting.libraries.repository.DefaultMimeRegistry;
import org.pentaho.reporting.libraries.repository.MimeRegistry;
import org.pentaho.reporting.libraries.repository.UrlRepository;

/** VFS-backed repository for multi-file HTML report output. */
public class FileObjectRepository implements UrlRepository, Serializable {
  private static final long serialVersionUID = -6221548332596506480L;

  private final MimeRegistry mimeRegistry;
  private final FileObjectContentLocation root;

  public FileObjectRepository(FileObject file) throws ContentIOException {
    this(file, new DefaultMimeRegistry());
  }

  public FileObjectRepository(FileObject file, MimeRegistry mimeRegistry) throws ContentIOException {
    if (mimeRegistry == null) {
      throw new NullPointerException("MimeRegistry must be given");
    }
    if (file == null) {
      throw new NullPointerException("File must be given");
    }
    this.mimeRegistry = mimeRegistry;
    this.root = new FileObjectContentLocation(this, file);
  }

  @Override
  public MimeRegistry getMimeRegistry() {
    return mimeRegistry;
  }

  @Override
  public ContentLocation getRoot() {
    return root;
  }

  @Override
  public URL getURL() throws MalformedURLException {
    try {
      return root.getBackend().getURL();
    } catch (FileSystemException e) {
      throw new RuntimeException(e);
    }
  }
}

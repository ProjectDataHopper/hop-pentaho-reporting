/*
 * Copyright (C) 2026 Project Data Hopper
 *
 * Ported from the historically LGPL-licensed PDI reporting VFS repository bridge.
 * Distributed under LGPL-2.1 with this plugin.
 */

package org.projectdatahopper.hop.pipeline.transforms.pentahoreporting.urlrepository;

import java.io.Serializable;
import java.util.Date;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.pentaho.reporting.libraries.repository.ContentEntity;
import org.pentaho.reporting.libraries.repository.ContentLocation;
import org.pentaho.reporting.libraries.repository.LibRepositoryBoot;
import org.pentaho.reporting.libraries.repository.Repository;

/** Content entity backed by a VFS {@link FileObject}. */
public abstract class FileObjectContentEntity implements ContentEntity, Serializable {
  private static final long serialVersionUID = 3962114134995757847L;

  private final FileObject backend;
  private final ContentLocation parent;
  private final Repository repository;

  protected FileObjectContentEntity(ContentLocation parent, FileObject backend) {
    if (backend == null) {
      throw new NullPointerException("Backend file must be given.");
    }
    if (parent == null) {
      throw new NullPointerException("Parent file must be given.");
    }
    this.repository = parent.getRepository();
    this.parent = parent;
    this.backend = backend;
  }

  protected FileObjectContentEntity(Repository repository, FileObject backend) {
    if (backend == null) {
      throw new NullPointerException("Backend file must be given.");
    }
    if (repository == null) {
      throw new NullPointerException("Repository file must be given.");
    }
    this.repository = repository;
    this.parent = null;
    this.backend = backend;
  }

  @Override
  public Repository getRepository() {
    return repository;
  }

  @Override
  public String getName() {
    return backend.getName().getBaseName();
  }

  protected FileObject getBackend() {
    return backend;
  }

  @Override
  public Object getContentId() {
    return backend;
  }

  @Override
  public Object getAttribute(String domain, String key) {
    try {
      if (LibRepositoryBoot.REPOSITORY_DOMAIN.equals(domain)) {
        if (LibRepositoryBoot.SIZE_ATTRIBUTE.equals(key)) {
          return backend.getContent().getSize();
        } else if (LibRepositoryBoot.VERSION_ATTRIBUTE.equals(key)) {
          return new Date(backend.getContent().getLastModifiedTime());
        }
      }
    } catch (FileSystemException ex) {
      throw new RuntimeException(ex);
    }
    return null;
  }

  @Override
  public boolean setAttribute(String domain, String key, Object value) {
    try {
      if (LibRepositoryBoot.REPOSITORY_DOMAIN.equals(domain)
          && LibRepositoryBoot.VERSION_ATTRIBUTE.equals(key)) {
        if (value instanceof Date date) {
          backend.getContent().setLastModifiedTime(date.getTime());
          return true;
        } else if (value instanceof Number time) {
          backend.getContent().setLastModifiedTime(time.longValue());
          return true;
        }
      }
      return false;
    } catch (FileSystemException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public ContentLocation getParent() {
    return parent;
  }

  @Override
  public boolean delete() {
    try {
      return backend.delete();
    } catch (FileSystemException ex) {
      throw new RuntimeException(ex);
    }
  }
}

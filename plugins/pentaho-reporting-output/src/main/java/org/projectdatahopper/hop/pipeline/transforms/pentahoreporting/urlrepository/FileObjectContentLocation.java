/*
 * Copyright (C) 2026 Project Data Hopper
 *
 * Ported from the historically LGPL-licensed PDI reporting VFS repository bridge.
 * Distributed under LGPL-2.1 with this plugin.
 */

package org.projectdatahopper.hop.pipeline.transforms.pentahoreporting.urlrepository;

import java.io.File;
import java.io.IOException;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.pentaho.reporting.libraries.repository.ContentCreationException;
import org.pentaho.reporting.libraries.repository.ContentEntity;
import org.pentaho.reporting.libraries.repository.ContentIOException;
import org.pentaho.reporting.libraries.repository.ContentItem;
import org.pentaho.reporting.libraries.repository.ContentLocation;
import org.pentaho.reporting.libraries.repository.Repository;
import org.pentaho.reporting.libraries.repository.RepositoryUtilities;

/** Content location backed by a VFS directory. */
public class FileObjectContentLocation extends FileObjectContentEntity implements ContentLocation {
  private static final long serialVersionUID = -5452372293937107734L;

  public FileObjectContentLocation(ContentLocation parent, FileObject backend)
      throws ContentIOException {
    super(parent, backend);
    assertDirectory(backend);
  }

  public FileObjectContentLocation(Repository repository, FileObject backend)
      throws ContentIOException {
    super(repository, backend);
    assertDirectory(backend);
  }

  private static void assertDirectory(FileObject backend) throws ContentIOException {
    try {
      if (!backend.exists() || !backend.isFolder()) {
        throw new ContentIOException("The given backend-file is not a directory.");
      }
    } catch (FileSystemException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ContentEntity[] listContents() throws ContentIOException {
    try {
      FileObject file = getBackend();
      FileObject[] files = file.getChildren();
      ContentEntity[] entities = new ContentEntity[files.length];
      for (int i = 0; i < files.length; i++) {
        FileObject child = files[i];
        if (RepositoryUtilities.isInvalidPathName(child.getName().getBaseName())) {
          continue;
        }
        if (child.isFolder()) {
          entities[i] = new FileObjectContentLocation(this, child);
        } else if (child.isFile()) {
          entities[i] = new FileObjectContentItem(this, child);
        }
      }
      return entities;
    } catch (FileSystemException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ContentEntity getEntry(String name) throws ContentIOException {
    try {
      if (RepositoryUtilities.isInvalidPathName(name)) {
        throw new IllegalArgumentException("The name given is not valid.");
      }
      FileObject child = getBackend().resolveFile(name);
      if (!child.exists()) {
        throw new ContentIOException("Not found:" + child);
      }
      if (child.isFolder()) {
        return new FileObjectContentLocation(this, child);
      } else if (child.isFile()) {
        return new FileObjectContentItem(this, child);
      } else {
        throw new ContentIOException("Not File nor directory.");
      }
    } catch (FileSystemException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ContentItem createItem(String name) throws ContentCreationException {
    String fileName = new File(name).getName();
    if (RepositoryUtilities.isInvalidPathName(fileName)) {
      throw new IllegalArgumentException("The name given is not valid.");
    }
    try {
      FileObject child = getBackend().resolveFile(fileName);
      if (child.exists()) {
        if (child.getContent().getSize() == 0) {
          return new FileObjectContentItem(this, child);
        }
        throw new ContentCreationException("File already exists: " + child);
      }
      try {
        child.createFile();
        return new FileObjectContentItem(this, child);
      } catch (IOException e) {
        throw new ContentCreationException("IOError while create", e);
      }
    } catch (FileSystemException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ContentLocation createLocation(String name) throws ContentCreationException {
    if (RepositoryUtilities.isInvalidPathName(name)) {
      throw new IllegalArgumentException("The name given is not valid.");
    }
    try {
      FileObject child = getBackend().resolveFile(name);
      if (child.exists()) {
        throw new ContentCreationException("File already exists.");
      }
      child.createFolder();
      try {
        return new FileObjectContentLocation(this, child);
      } catch (ContentIOException e) {
        throw new ContentCreationException("Failed to create the content-location", e);
      }
    } catch (FileSystemException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean exists(String name) {
    if (RepositoryUtilities.isInvalidPathName(name)) {
      return false;
    }
    try {
      return getBackend().resolveFile(name).exists();
    } catch (FileSystemException e) {
      throw new RuntimeException(e);
    }
  }
}

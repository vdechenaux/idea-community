package com.intellij.debugger.engine;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.NoDataException;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Collections;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class CompoundPositionManager implements PositionManager{
  private final ArrayList<PositionManager> myPositionManagers = new ArrayList<PositionManager>();

  public CompoundPositionManager() {
  }

  public CompoundPositionManager(PositionManager manager) {
    appendPositionManager(manager);
  }

  public void appendPositionManager(PositionManager manager) {
    myPositionManagers.remove(manager);
    myPositionManagers.add(0, manager);
  }

  public SourcePosition getSourcePosition(Location location) {
    for (Iterator<PositionManager> iterator = myPositionManagers.iterator(); iterator.hasNext();) {
      PositionManager positionManager = iterator.next();
      try {
        return positionManager.getSourcePosition(location);
      }
      catch (NoDataException e) {
      }
    }
    return null;
  }

  public List<ReferenceType> getAllClasses(SourcePosition classPosition) {
    for (Iterator<PositionManager> iterator = myPositionManagers.iterator(); iterator.hasNext();) {
      PositionManager positionManager = iterator.next();
      try {
        return positionManager.getAllClasses(classPosition);
      }
      catch (NoDataException e) {
      }
    }
    return Collections.EMPTY_LIST;
  }

  public List<Location> locationsOfLine(ReferenceType type, SourcePosition position) {
    for (Iterator<PositionManager> iterator = myPositionManagers.iterator(); iterator.hasNext();) {
      PositionManager positionManager = iterator.next();
      try {
        return positionManager.locationsOfLine(type, position);
      }
      catch (NoDataException e) {
      }
    }
    return Collections.EMPTY_LIST;
  }

  public ClassPrepareRequest createPrepareRequest(ClassPrepareRequestor requestor, SourcePosition position) {
    for (Iterator<PositionManager> iterator = myPositionManagers.iterator(); iterator.hasNext();) {
      PositionManager positionManager = iterator.next();

      try {
        return positionManager.createPrepareRequest(requestor, position);
      }
      catch (NoDataException e) {
      }
    }

    return null;
  }
}

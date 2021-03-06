package com.intellij.tasks.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
@State(
  name = "RecentTaskRepositories",
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class RecentTaskRepositories implements PersistentStateComponent<Element> {

  private final Set<TaskRepository> myRepositories = new THashSet<TaskRepository>(HASHING_STRATEGY);

  private static final TObjectHashingStrategy<TaskRepository> HASHING_STRATEGY = new TObjectHashingStrategy<TaskRepository>() {
    public int computeHashCode(TaskRepository object) {
      return object.getUrl() == null ? 0 : object.getUrl().hashCode();
    }

    public boolean equals(TaskRepository o1, TaskRepository o2) {
      return Comparing.equal(o1.getUrl(), o2.getUrl());
    }
  };

  public static RecentTaskRepositories getInstance() {
    return ServiceManager.getService(RecentTaskRepositories.class);
  }

  public Set<TaskRepository> getRepositories() {
    return new THashSet<TaskRepository>(ContainerUtil.findAll(myRepositories, new Condition<TaskRepository>() {
      public boolean value(TaskRepository repository) {
        return !StringUtil.isEmptyOrSpaces(repository.getUrl());
      }
    }), HASHING_STRATEGY);
  }

  public void addRepositories(Collection<TaskRepository> repositories) {
    Collection<TaskRepository> old = new ArrayList<TaskRepository>(myRepositories);
    myRepositories.clear();
    if (doAddReps(repositories)) return;
    doAddReps(old);
  }

  private boolean doAddReps(Collection<TaskRepository> repositories) {
    for (TaskRepository repository : repositories) {
      if (!StringUtil.isEmptyOrSpaces(repository.getUrl())) {
        if (myRepositories.size() == 10) {
          return true;
        }
        myRepositories.add(repository);
      }
    }
    return false;
  }

  public Element getState() {
    return XmlSerializer.serialize(myRepositories.toArray(new TaskRepository[myRepositories.size()]));
  }

  public void loadState(Element state) {
    myRepositories.clear();
    myRepositories.addAll(TaskManagerImpl.loadRepositories(state));
  }
}

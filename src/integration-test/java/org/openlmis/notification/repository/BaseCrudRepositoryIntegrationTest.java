/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org.
 */

package org.openlmis.notification.repository;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openlmis.notification.domain.Identifiable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;


@RunWith(SpringRunner.class)
@SpringBootTest(properties = { "notificationToSend.autoStartup=false" })
@ActiveProfiles({"test", "test-run"})
@Transactional
public abstract class BaseCrudRepositoryIntegrationTest
    <T extends Identifiable<I>, I extends Serializable> {

  abstract CrudRepository<T, I> getRepository();

  /*
   * Generate a unique instance of given type.
   * @return generated instance
   */
  abstract T generateInstance() throws Exception;

  private AtomicInteger instanceNumber = new AtomicInteger(0);

  int getNextInstanceNumber() {
    return this.instanceNumber.incrementAndGet();
  }

  protected void assertBefore(T instance) {
    Assert.assertNull(instance.getId());
  }

  protected void assertInstance(T instance) {
    Assert.assertNotNull(instance.getId());
  }

  @Test
  public void shouldCreate() throws Exception {
    CrudRepository<T, I> repository = this.getRepository();

    T instance = this.generateInstance();
    assertBefore(instance);

    instance = repository.save(instance);
    assertInstance(instance);

    I id = instance.getId();

    Assert.assertTrue(repository.existsById(id));
  }

  @Test
  public void shouldFindOne() throws Exception {
    CrudRepository<T, I> repository = this.getRepository();

    T instance = this.generateInstance();

    instance = repository.save(instance);
    assertInstance(instance);

    I id = instance.getId();

    instance = repository.findById(id).orElse(null);
    assertInstance(instance);
    Assert.assertEquals(id, instance.getId());
  }

  @Test
  public void shouldDelete() throws Exception {
    CrudRepository<T, I> repository = this.getRepository();

    T instance = this.generateInstance();
    Assert.assertNotNull(instance);

    instance = repository.save(instance);
    assertInstance(instance);

    I id = instance.getId();

    repository.deleteById(id);
    Assert.assertFalse(repository.existsById(id));
  }
}

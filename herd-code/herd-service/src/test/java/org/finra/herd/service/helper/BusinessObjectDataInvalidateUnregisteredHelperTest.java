/*
* Copyright 2015 herd contributors
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.finra.herd.service.helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.finra.herd.dao.S3Operations;
import org.finra.herd.model.ObjectNotFoundException;
import org.finra.herd.model.api.xml.BusinessObjectData;
import org.finra.herd.model.api.xml.BusinessObjectDataInvalidateUnregisteredRequest;
import org.finra.herd.model.api.xml.BusinessObjectDataInvalidateUnregisteredResponse;
import org.finra.herd.model.api.xml.StorageUnit;
import org.finra.herd.model.jpa.BusinessObjectDataEntity;
import org.finra.herd.model.jpa.BusinessObjectDataStatusEntity;
import org.finra.herd.model.jpa.BusinessObjectFormatEntity;
import org.finra.herd.model.jpa.StorageEntity;
import org.finra.herd.service.AbstractServiceTest;

public class BusinessObjectDataInvalidateUnregisteredHelperTest extends AbstractServiceTest
{
    @Autowired
    private S3Operations s3Operations;

    @After
    public void after()
    {
        s3Operations.rollback();
    }

    /**
     * Test case where S3 and herd are in sync because there are no data in either S3 or herd. Expects no new registrations. This is a happy path where common
     * response values are asserted.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataS30Herd0() throws Exception
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        try
        {
            businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call the API
        BusinessObjectDataInvalidateUnregisteredResponse actualResponse =
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);

        // Make assertions
        Assert.assertNotNull("response is null", actualResponse);
        Assert.assertEquals("response namespace", request.getNamespace(), actualResponse.getNamespace());
        Assert.assertEquals("response business object definition name", request.getBusinessObjectDefinitionName(),
            actualResponse.getBusinessObjectDefinitionName());
        Assert.assertEquals("response business object format usage", request.getBusinessObjectFormatUsage(), actualResponse.getBusinessObjectFormatUsage());
        Assert.assertEquals("response business object format file type", request.getBusinessObjectFormatFileType(),
            actualResponse.getBusinessObjectFormatFileType());
        Assert
            .assertEquals("response business object format version", request.getBusinessObjectFormatVersion(), actualResponse.getBusinessObjectFormatVersion());
        Assert.assertEquals("response partition value", request.getPartitionValue(), actualResponse.getPartitionValue());
        Assert.assertEquals("response sub-partition values", request.getSubPartitionValues(), actualResponse.getSubPartitionValues());
        Assert.assertEquals("response storage name", request.getStorageName(), actualResponse.getStorageName());
        Assert.assertNotNull("response business object datas is null", actualResponse.getRegisteredBusinessObjectDataList());
        Assert.assertEquals("response business object datas size", 0, actualResponse.getRegisteredBusinessObjectDataList().size());
    }

    /**
     * Test case where herd and S3 are in sync because both have 1 object registered. Expects no new data registration.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataS31Herd1() throws Exception
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        try
        {
            BusinessObjectFormatEntity businessObjectFormatEntity = businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);

            businessObjectDataServiceTestHelper.createS3Object(businessObjectFormatEntity, request, 0);
            createBusinessObjectDataEntityFromBusinessObjectDataInvalidateUnregisteredRequest(businessObjectFormatEntity, request, 0, true);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call the API
        BusinessObjectDataInvalidateUnregisteredResponse actualResponse =
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);

        // Make assertions
        Assert.assertNotNull("response business object datas is null", actualResponse.getRegisteredBusinessObjectDataList());
        Assert.assertEquals("response business object datas size", 0, actualResponse.getRegisteredBusinessObjectDataList().size());
    }

    /**
     * Test case where S3 has 1 object, and herd has no object registered. Expects one new registration in INVALID status.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataS31Herd0() throws Exception
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        // Given an object in S3
        BusinessObjectFormatEntity businessObjectFormatEntity;
        try
        {
            businessObjectFormatEntity = businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);

            businessObjectDataServiceTestHelper.createS3Object(businessObjectFormatEntity, request, 0);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call API
        BusinessObjectDataInvalidateUnregisteredResponse actualResponse =
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);

        // Make assertions
        Assert.assertNotNull("response business object datas is null", actualResponse.getRegisteredBusinessObjectDataList());
        Assert.assertEquals("response business object datas size", 1, actualResponse.getRegisteredBusinessObjectDataList().size());
        {
            BusinessObjectData businessObjectData = actualResponse.getRegisteredBusinessObjectDataList().get(0);
            Assert.assertEquals("response business object data[0] version", 0, businessObjectData.getVersion());
            Assert.assertEquals("response business object data[0] status", BusinessObjectDataInvalidateUnregisteredHelper.UNREGISTERED_STATUS,
                businessObjectData.getStatus());
            Assert.assertNotNull("response business object data[0] storage units is null", businessObjectData.getStorageUnits());
            Assert.assertEquals("response business object data[0] storage units size", 1, businessObjectData.getStorageUnits().size());
            {
                String expectedS3KeyPrefix = s3KeyPrefixHelper.buildS3KeyPrefix(S3_KEY_PREFIX_VELOCITY_TEMPLATE, businessObjectFormatEntity,
                    businessObjectDataHelper.createBusinessObjectDataKey(businessObjectData), STORAGE_NAME);
                StorageUnit storageUnit = businessObjectData.getStorageUnits().get(0);
                Assert.assertNotNull("response business object data[0] storage unit[0] storage directory is null", storageUnit.getStorageDirectory());
                Assert.assertEquals("response business object data[0] storage unit[0] storage directory path", expectedS3KeyPrefix,
                    storageUnit.getStorageDirectory().getDirectoryPath());
            }
        }
    }

    /**
     * Test case where S3 has 1 object, and herd has no object registered. The data has sub-partitions. Expects one new registration in INVALID status.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataS31Herd0WithSubPartitions() throws Exception
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        // Given an object in S3
        BusinessObjectFormatEntity businessObjectFormatEntity;
        try
        {
            businessObjectFormatEntity = businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);

            businessObjectDataServiceTestHelper.createS3Object(businessObjectFormatEntity, request, 0);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call API
        BusinessObjectDataInvalidateUnregisteredResponse actualResponse =
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);

        // Make assertions
        Assert.assertNotNull("response sub-partition values is null", actualResponse.getSubPartitionValues());
        Assert.assertEquals("response sub-partition values", request.getSubPartitionValues(), actualResponse.getSubPartitionValues());
        Assert.assertNotNull("response business object datas is null", actualResponse.getRegisteredBusinessObjectDataList());
        Assert.assertEquals("response business object datas size", 1, actualResponse.getRegisteredBusinessObjectDataList().size());
        {
            BusinessObjectData businessObjectData = actualResponse.getRegisteredBusinessObjectDataList().get(0);
            Assert.assertEquals("response business object data[0] version", 0, businessObjectData.getVersion());
            Assert.assertEquals("response business object data[0] status", BusinessObjectDataInvalidateUnregisteredHelper.UNREGISTERED_STATUS,
                businessObjectData.getStatus());
            Assert.assertNotNull("response business object data[0] storage units is null", businessObjectData.getStorageUnits());
            Assert.assertEquals("response business object data[0] storage units size", 1, businessObjectData.getStorageUnits().size());
            {
                String expectedS3KeyPrefix = s3KeyPrefixHelper.buildS3KeyPrefix(S3_KEY_PREFIX_VELOCITY_TEMPLATE, businessObjectFormatEntity,
                    businessObjectDataHelper.createBusinessObjectDataKey(businessObjectData), STORAGE_NAME);
                StorageUnit storageUnit = businessObjectData.getStorageUnits().get(0);
                Assert.assertNotNull("response business object data[0] storage unit[0] storage directory is null", storageUnit.getStorageDirectory());
                Assert.assertEquals("response business object data[0] storage unit[0] storage directory path", expectedS3KeyPrefix,
                    storageUnit.getStorageDirectory().getDirectoryPath());
            }
        }
    }

    /**
     * Test case where S3 has 2 objects, and herd has 1 object registered. Expects one new registration in INVALID status.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataS32Herd1() throws Exception
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        // Given 1 business object data registered
        // Given 2 S3 objects
        BusinessObjectFormatEntity businessObjectFormatEntity;
        try
        {
            businessObjectFormatEntity = businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);

            createBusinessObjectDataEntityFromBusinessObjectDataInvalidateUnregisteredRequest(businessObjectFormatEntity, request, 0, true);
            businessObjectDataServiceTestHelper.createS3Object(businessObjectFormatEntity, request, 0);
            businessObjectDataServiceTestHelper.createS3Object(businessObjectFormatEntity, request, 1);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call API
        BusinessObjectDataInvalidateUnregisteredResponse actualResponse =
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);

        // Make assertions
        Assert.assertNotNull("response business object datas is null", actualResponse.getRegisteredBusinessObjectDataList());
        Assert.assertEquals("response business object datas size", 1, actualResponse.getRegisteredBusinessObjectDataList().size());
        {
            BusinessObjectData businessObjectData = actualResponse.getRegisteredBusinessObjectDataList().get(0);
            Assert.assertEquals("response business object data[0] version", 1, businessObjectData.getVersion());
            Assert.assertEquals("response business object data[0] status", BusinessObjectDataInvalidateUnregisteredHelper.UNREGISTERED_STATUS,
                businessObjectData.getStatus());
            Assert.assertNotNull("response business object data[0] storage units is null", businessObjectData.getStorageUnits());
            Assert.assertEquals("response business object data[0] storage units size", 1, businessObjectData.getStorageUnits().size());
            {
                String expectedS3KeyPrefix = s3KeyPrefixHelper.buildS3KeyPrefix(S3_KEY_PREFIX_VELOCITY_TEMPLATE, businessObjectFormatEntity,
                    businessObjectDataHelper.createBusinessObjectDataKey(businessObjectData), STORAGE_NAME);
                StorageUnit storageUnit = businessObjectData.getStorageUnits().get(0);
                Assert.assertNotNull("response business object data[0] storage unit[0] storage directory is null", storageUnit.getStorageDirectory());
                Assert.assertEquals("response business object data[0] storage unit[0] storage directory path", expectedS3KeyPrefix,
                    storageUnit.getStorageDirectory().getDirectoryPath());
            }
        }
    }

    /**
     * Test case where S3 has 2 objects, but herd has no object registered. Expects 2 new registrations in INVALID status.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataS32Herd0() throws Exception
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        // Given 1 business object data registered
        // Given 2 S3 objects
        BusinessObjectFormatEntity businessObjectFormatEntity;
        try
        {
            businessObjectFormatEntity = businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);

            businessObjectDataServiceTestHelper.createS3Object(businessObjectFormatEntity, request, 0);
            businessObjectDataServiceTestHelper.createS3Object(businessObjectFormatEntity, request, 1);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call API
        BusinessObjectDataInvalidateUnregisteredResponse actualResponse =
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);

        // Make assertions
        Assert.assertNotNull("response business object datas is null", actualResponse.getRegisteredBusinessObjectDataList());
        Assert.assertEquals("response business object datas size", 2, actualResponse.getRegisteredBusinessObjectDataList().size());
        // Assert first data registered
        {
            BusinessObjectData businessObjectData = actualResponse.getRegisteredBusinessObjectDataList().get(0);
            Assert.assertEquals("response business object data[0] version", 0, businessObjectData.getVersion());
            Assert.assertEquals("response business object data[0] status", BusinessObjectDataInvalidateUnregisteredHelper.UNREGISTERED_STATUS,
                businessObjectData.getStatus());
            Assert.assertNotNull("response business object data[0] storage units is null", businessObjectData.getStorageUnits());
            Assert.assertEquals("response business object data[0] storage units size", 1, businessObjectData.getStorageUnits().size());
            {
                String expectedS3KeyPrefix = s3KeyPrefixHelper.buildS3KeyPrefix(S3_KEY_PREFIX_VELOCITY_TEMPLATE, businessObjectFormatEntity,
                    businessObjectDataHelper.createBusinessObjectDataKey(businessObjectData), STORAGE_NAME);
                StorageUnit storageUnit = businessObjectData.getStorageUnits().get(0);
                Assert.assertNotNull("response business object data[0] storage unit[0] storage directory is null", storageUnit.getStorageDirectory());
                Assert.assertEquals("response business object data[0] storage unit[0] storage directory path", expectedS3KeyPrefix,
                    storageUnit.getStorageDirectory().getDirectoryPath());
            }
        }
        // Assert second data registered
        {
            BusinessObjectData businessObjectData = actualResponse.getRegisteredBusinessObjectDataList().get(1);
            Assert.assertEquals("response business object data[1] version", 1, businessObjectData.getVersion());
            Assert.assertEquals("response business object data[1] status", BusinessObjectDataInvalidateUnregisteredHelper.UNREGISTERED_STATUS,
                businessObjectData.getStatus());
            Assert.assertNotNull("response business object data[1] storage units is null", businessObjectData.getStorageUnits());
            Assert.assertEquals("response business object data[1] storage units size", 1, businessObjectData.getStorageUnits().size());
            {
                String expectedS3KeyPrefix = s3KeyPrefixHelper.buildS3KeyPrefix(S3_KEY_PREFIX_VELOCITY_TEMPLATE, businessObjectFormatEntity,
                    businessObjectDataHelper.createBusinessObjectDataKey(businessObjectData), STORAGE_NAME);
                StorageUnit storageUnit = businessObjectData.getStorageUnits().get(0);
                Assert.assertNotNull("response business object data[1] storage unit[0] storage directory is null", storageUnit.getStorageDirectory());
                Assert.assertEquals("response business object data[1] storage unit[0] storage directory path", expectedS3KeyPrefix,
                    storageUnit.getStorageDirectory().getDirectoryPath());
            }
        }
    }

    /**
     * Test case where S3 has 1 object, and herd has no object registered. The S3 object is registered under version 1 so there is a gap for version 0 of
     * registration. Expects no new registrations since the API does not consider the S3 objects after a gap.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataS31Herd0WithGap()
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        // Given an object in S3
        BusinessObjectFormatEntity businessObjectFormatEntity;
        try
        {
            businessObjectFormatEntity = businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);

            businessObjectDataServiceTestHelper.createS3Object(businessObjectFormatEntity, request, 1);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call API
        BusinessObjectDataInvalidateUnregisteredResponse actualResponse =
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);

        // Make assertions
        Assert.assertNotNull("response business object datas is null", actualResponse.getRegisteredBusinessObjectDataList());
        Assert.assertEquals("response business object datas size", 0, actualResponse.getRegisteredBusinessObjectDataList().size());
    }

    /**
     * The prefix search for S3 object should match prefixed directories, not sub-strings. For example: - If an S3 object exists with key "c/b/aa/test.txt" - If
     * a search for prefix "c/b/a" is executed - The S3 object should NOT match, since it is a prefix, but not a prefix directory.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataS3PrefixWithSlash() throws Exception
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        // Given an object in S3
        BusinessObjectFormatEntity businessObjectFormatEntity;
        try
        {
            businessObjectFormatEntity = businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);

            request.setPartitionValue("AA"); // Create S3 object which is contains the partition value as substring
            businessObjectDataServiceTestHelper.createS3Object(businessObjectFormatEntity, request, 0);

            request.setPartitionValue("A"); // Send request with substring
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call API
        BusinessObjectDataInvalidateUnregisteredResponse actualResponse =
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);

        // Make assertions, expect no data updates since nothing should match
        Assert.assertNotNull("response business object datas is null", actualResponse.getRegisteredBusinessObjectDataList());
        Assert.assertEquals("response business object datas size", 0, actualResponse.getRegisteredBusinessObjectDataList().size());
    }

    /**
     * Asserts that namespace requiredness validation is working.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataValidationNamespaceRequired()
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);
        request.setNamespace(BLANK_TEXT);

        // Given a business object format
        try
        {
            businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call the API
        try
        {
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);
            Assert.fail("expected a IllegalArgumentException, but no exception was thrown");
        }
        catch (Exception e)
        {
            Assert.assertEquals("thrown exception type", IllegalArgumentException.class, e.getClass());
            Assert.assertEquals("thrown exception message", "The namespace is required", e.getMessage());
        }
    }

    /**
     * Asserts that business object definition name requiredness validation is working.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataValidationBusinessObjectDefinitionNameRequired()
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BLANK_TEXT, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION,
                PARTITION_VALUE, NO_SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        try
        {
            businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call the API
        try
        {
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);
            Assert.fail("expected a IllegalArgumentException, but no exception was thrown");
        }
        catch (Exception e)
        {
            Assert.assertEquals("thrown exception type", IllegalArgumentException.class, e.getClass());
            Assert.assertEquals("thrown exception message", "The business object definition name is required", e.getMessage());
        }
    }

    /**
     * Asserts that business object format usage requiredness validation is working.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataValidationBusinessObjectFormatUsageRequired()
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, BLANK_TEXT, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        try
        {
            businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call the API
        try
        {
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);
            Assert.fail("expected a IllegalArgumentException, but no exception was thrown");
        }
        catch (Exception e)
        {
            Assert.assertEquals("thrown exception type", IllegalArgumentException.class, e.getClass());
            Assert.assertEquals("thrown exception message", "The business object format usage is required", e.getMessage());
        }
    }

    /**
     * Business object format must exist for this API to work
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataValidationBusinessObjectFormatMustExist()
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        try
        {
            businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Modify a parameter specific to a format to reference a format that does not exist
        request.setBusinessObjectFormatFileType("DOES_NOT_EXIST");

        // Call the API
        try
        {
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);
            Assert.fail("expected a ObjectNotFoundException, but no exception was thrown");
        }
        catch (Exception e)
        {
            Assert.assertEquals("thrown exception type", ObjectNotFoundException.class, e.getClass());
            Assert.assertEquals("thrown exception message",
                "Business object format with namespace \"" + request.getNamespace() + "\", business object definition name \"" +
                    request.getBusinessObjectDefinitionName() + "\", format usage \"" + request.getBusinessObjectFormatUsage() + "\", format file type \"" +
                    request.getBusinessObjectFormatFileType() + "\", and format version \"" + request.getBusinessObjectFormatVersion() + "\" doesn't exist.",
                e.getMessage());
        }
    }

    /**
     * Asserts that business object format file type requiredness validation is working.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataValidationBusinessObjectFormatFileTypeRequired()
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, BLANK_TEXT, FORMAT_VERSION, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        try
        {
            businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call the API
        try
        {
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);
            Assert.fail("expected a IllegalArgumentException, but no exception was thrown");
        }
        catch (Exception e)
        {
            Assert.assertEquals("thrown exception type", IllegalArgumentException.class, e.getClass());
            Assert.assertEquals("thrown exception message", "The business object format file type is required", e.getMessage());
        }
    }

    /**
     * Asserts that business object format version requiredness validation is working.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataValidationBusinessObjectFormatVersionRequired()
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        try
        {
            businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // nullify version after format is created so that the format is created correctly.
        request.setBusinessObjectFormatVersion(null);

        // Call the API
        try
        {
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);
            Assert.fail("expected a IllegalArgumentException, but no exception was thrown");
        }
        catch (Exception e)
        {
            Assert.assertEquals("thrown exception type", IllegalArgumentException.class, e.getClass());
            Assert.assertEquals("thrown exception message", "The business object format version is required", e.getMessage());
        }
    }

    /**
     * Asserts that business object format version positive validation is working.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataValidationBusinessObjectFormatVersionNegative()
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, -1, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        try
        {
            businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call the API
        try
        {
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);
            Assert.fail("expected a IllegalArgumentException, but no exception was thrown");
        }
        catch (Exception e)
        {
            Assert.assertEquals("thrown exception type", IllegalArgumentException.class, e.getClass());
            Assert.assertEquals("thrown exception message", "The business object format version must be greater than or equal to 0", e.getMessage());
        }
    }

    /**
     * Asserts that partition value requiredness validation is working.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataValidationPartitionValueRequired()
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, BLANK_TEXT,
                NO_SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        try
        {
            businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call the API
        try
        {
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);
            Assert.fail("expected a IllegalArgumentException, but no exception was thrown");
        }
        catch (Exception e)
        {
            Assert.assertEquals("thrown exception type", IllegalArgumentException.class, e.getClass());
            Assert.assertEquals("thrown exception message", "The partition value is required", e.getMessage());
        }
    }

    /**
     * Asserts that storage name requiredness validation is working.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataValidationStorageNameRequired()
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, BLANK_TEXT);

        // Given a business object format
        try
        {
            businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call the API
        try
        {
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);
            Assert.fail("expected a IllegalArgumentException, but no exception was thrown");
        }
        catch (Exception e)
        {
            Assert.assertEquals("thrown exception type", IllegalArgumentException.class, e.getClass());
            Assert.assertEquals("thrown exception message", "The storage name is required", e.getMessage());
        }
    }

    /**
     * Storage must exist for this API to work.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataValidationStorageMustExist()
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, "DOES_NOT_EXIST");

        // Given a business object format
        try
        {
            businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call the API
        try
        {
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);
            Assert.fail("expected a ObjectNotFoundException, but no exception was thrown");
        }
        catch (Exception e)
        {
            Assert.assertEquals("thrown exception type", ObjectNotFoundException.class, e.getClass());
            Assert.assertEquals("thrown exception message", "Storage with name \"" + request.getStorageName() + "\" doesn't exist.", e.getMessage());
        }
    }

    /**
     * Storage is found, but the storage platform is not S3. This API only works for S3 platforms since it requires S3 key prefix.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataValidationStoragePlatformMustBeS3()
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                NO_SUBPARTITION_VALUES, STORAGE_NAME);

        // Given a business object format
        try
        {
            storageDaoTestHelper.createStorageEntity(request.getStorageName(), "NOT_S3");
            businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call the API
        try
        {
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);
            Assert.fail("expected a IllegalArgumentException, but no exception was thrown");
        }
        catch (Exception e)
        {
            Assert.assertEquals("thrown exception type", IllegalArgumentException.class, e.getClass());
            Assert.assertEquals("thrown exception message", "The specified storage '" + request.getStorageName() + "' is not an S3 storage platform.",
                e.getMessage());
        }
    }

    /**
     * If sub-partition values are given, they must not be blank.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataValidationSubPartitionValueNotBlank()
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                Arrays.asList(BLANK_TEXT), StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        try
        {
            businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // Call the API
        try
        {
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);
            Assert.fail("expected a IllegalArgumentException, but no exception was thrown");
        }
        catch (Exception e)
        {
            Assert.assertEquals("thrown exception type", IllegalArgumentException.class, e.getClass());
            Assert.assertEquals("thrown exception message", "The sub-partition value [0] must not be blank", e.getMessage());
        }
    }

    /**
     * Asserts that values are trimmed before the request is processed.
     */
    @Test
    public void testInvalidateUnregisteredBusinessObjectDataTrim() throws Exception
    {
        BusinessObjectDataInvalidateUnregisteredRequest request =
            new BusinessObjectDataInvalidateUnregisteredRequest(NAMESPACE, BDEF_NAME, FORMAT_USAGE_CODE, FORMAT_FILE_TYPE_CODE, FORMAT_VERSION, PARTITION_VALUE,
                SUBPARTITION_VALUES, StorageEntity.MANAGED_STORAGE);

        // Given a business object format
        try
        {
            businessObjectFormatServiceTestHelper.createBusinessObjectFormat(request);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Test failed during setup. Most likely setup or developer error.", e);
        }

        // pad string values with white spaces
        request.setNamespace(BLANK_TEXT + request.getNamespace() + BLANK_TEXT);
        request.setBusinessObjectDefinitionName(BLANK_TEXT + request.getBusinessObjectDefinitionName() + BLANK_TEXT);
        request.setBusinessObjectFormatFileType(BLANK_TEXT + request.getBusinessObjectFormatFileType() + BLANK_TEXT);
        request.setBusinessObjectFormatUsage(BLANK_TEXT + request.getBusinessObjectFormatUsage() + BLANK_TEXT);
        request.setPartitionValue(BLANK_TEXT + request.getPartitionValue() + BLANK_TEXT);
        List<String> paddedSubPartitionValues = new ArrayList<>();
        for (String subPartitionValue : request.getSubPartitionValues())
        {
            paddedSubPartitionValues.add(BLANK_TEXT + subPartitionValue + BLANK_TEXT);
        }
        request.setSubPartitionValues(paddedSubPartitionValues);

        // Call the API
        BusinessObjectDataInvalidateUnregisteredResponse actualResponse =
            businessObjectDataInvalidateUnregisteredHelper.invalidateUnregisteredBusinessObjectData(request);

        // Make assertions
            /*
             * Note: The API will modify the request to now contain the trimmed value.
             */
        Assert.assertNotNull("response is null", actualResponse);
        Assert.assertEquals("response namespace", request.getNamespace(), actualResponse.getNamespace());
        Assert.assertEquals("response business object definition name", request.getBusinessObjectDefinitionName(),
            actualResponse.getBusinessObjectDefinitionName());
        Assert.assertEquals("response business object format usage", request.getBusinessObjectFormatUsage(), actualResponse.getBusinessObjectFormatUsage());
        Assert.assertEquals("response business object format file type", request.getBusinessObjectFormatFileType(),
            actualResponse.getBusinessObjectFormatFileType());
        Assert
            .assertEquals("response business object format version", request.getBusinessObjectFormatVersion(), actualResponse.getBusinessObjectFormatVersion());
        Assert.assertEquals("response partition value", request.getPartitionValue(), actualResponse.getPartitionValue());
        Assert.assertEquals("response sub-partition values", request.getSubPartitionValues(), actualResponse.getSubPartitionValues());
        Assert.assertEquals("response storage name", request.getStorageName(), actualResponse.getStorageName());
        Assert.assertNotNull("response business object datas is null", actualResponse.getRegisteredBusinessObjectDataList());
        Assert.assertEquals("response business object datas size", 0, actualResponse.getRegisteredBusinessObjectDataList().size());
    }

    /**
     * Creates and persists a business object data entity per specified parameters.
     *
     * @param businessObjectFormatEntity the business object format entity
     * @param request the business object data invalidate unregistered request that contains the business object data key
     * @param businessObjectDataVersion the business object data version
     * @param latestVersion specifies if this business object data is the latest version
     *
     * @return the business object data entity
     */
    private BusinessObjectDataEntity createBusinessObjectDataEntityFromBusinessObjectDataInvalidateUnregisteredRequest(
        BusinessObjectFormatEntity businessObjectFormatEntity, BusinessObjectDataInvalidateUnregisteredRequest request, int businessObjectDataVersion,
        boolean latestVersion)
    {
        BusinessObjectDataEntity businessObjectDataEntity = new BusinessObjectDataEntity();

        businessObjectDataEntity.setBusinessObjectFormat(businessObjectFormatEntity);
        businessObjectDataEntity.setPartitionValue(request.getPartitionValue());
        businessObjectDataEntity.setPartitionValue2(herdCollectionHelper.safeGet(request.getSubPartitionValues(), 0));
        businessObjectDataEntity.setPartitionValue3(herdCollectionHelper.safeGet(request.getSubPartitionValues(), 1));
        businessObjectDataEntity.setPartitionValue4(herdCollectionHelper.safeGet(request.getSubPartitionValues(), 2));
        businessObjectDataEntity.setPartitionValue5(herdCollectionHelper.safeGet(request.getSubPartitionValues(), 3));
        businessObjectDataEntity.setVersion(businessObjectDataVersion);
        businessObjectDataEntity.setLatestVersion(latestVersion);
        businessObjectDataEntity.setStatus(businessObjectDataStatusDao.getBusinessObjectDataStatusByCode(BusinessObjectDataStatusEntity.VALID));

        return businessObjectDataDao.saveAndRefresh(businessObjectDataEntity);
    }
}

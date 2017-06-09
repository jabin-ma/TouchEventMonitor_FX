/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package application

import com.android.ddmlib.input.EventData
import javafx.beans.NamedArg
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.value.ObservableValue
import javafx.scene.control.TableColumn
import javafx.scene.control.TableColumn.CellDataFeatures
import javafx.util.Callback

import sun.util.logging.PlatformLogger.Level
import com.sun.javafx.property.PropertyReference
import com.sun.javafx.scene.control.Logging


class PropertyValueFactory(@param:NamedArg("property") val property: String) : Callback<TableColumn.CellDataFeatures<EventData, out Any>, ObservableValue<out Any>>{

    private var columnClass: Class<*>? = null
    private var previousProperty: String? = null
    private var propertyRef: PropertyReference<Any>? = null

    override fun call(param: CellDataFeatures<EventData, out Any>?): ObservableValue<Any>? {
        return getCellDataReflectively(param?.value)
    }

    private fun getCellDataReflectively(rowData: EventData?): ObservableValue<Any>? {
        if (property == null || property.isEmpty() || rowData == null) return null

        try {
            // we attempt to cache the property reference here, as otherwise
            // performance suffers when working in large data models. For
            // a bit of reference, refer to RT-13937.
            if (columnClass == null || previousProperty == null ||
                    columnClass != rowData.javaClass ||
                    previousProperty != property) {

                // create a new PropertyReference
                this.columnClass = rowData.javaClass
                this.previousProperty = property
                this.propertyRef = PropertyReference<Any>(rowData.javaClass, property)
            }

            if (propertyRef!!.hasProperty()) {
                return propertyRef!!.getProperty(rowData)
            } else {
                val value = propertyRef!!.get(rowData)
                return ReadOnlyObjectWrapper(value)
            }
        } catch (e: IllegalStateException) {
            // log the warning and move on
            val logger = Logging.getControlsLogger()
            if (logger.isLoggable(Level.WARNING)) {
                logger.finest("Can not retrieve property '" + property +
                        "' in PropertyValueFactory: " + this +
                        " with provided class type: " + rowData.javaClass, e)
            }
        }

        return null
    }
}

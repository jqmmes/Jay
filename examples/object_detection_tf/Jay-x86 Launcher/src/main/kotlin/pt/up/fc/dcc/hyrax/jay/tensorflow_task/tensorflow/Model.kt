/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package pt.up.fc.dcc.hyrax.jay.tensorflow_task.tensorflow

import pt.up.fc.dcc.hyrax.jay.proto.JayTensorFlowProto.Model as JayModel

data class Model(val modelId: Int,
                 val modelName: String,
                 val remoteUrl: String? = null,
                 var downloaded: Boolean = false,
                 var isQuantized: Boolean = false,
                 var inputSize: Int = 320) {

    internal constructor(request: JayModel?) : this(request!!.id, request.name, request.url, request.downloaded)

    fun getProto(): JayModel {
        return JayModel.newBuilder().setId(modelId).setName(modelName).setUrl(remoteUrl).setDownloaded(downloaded).setIsQuantized(isQuantized).setInputSize(inputSize).build()
    }
}
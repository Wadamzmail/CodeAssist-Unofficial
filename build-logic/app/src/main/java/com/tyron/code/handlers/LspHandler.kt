/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tyron.code.handlers

import com.tyron.completion.lsp.api.ILanguageClient
import com.tyron.completion.lsp.api.ILanguageServerRegistry
//import com.itsaky.androidide.lsp.java.JavaLanguageServer
//import com.itsaky.androidide.lsp.xml.XMLLanguageServer
import com.itsaky.androidide.lsp.kotlin.KotlinLanguageServer

/**
 *
 * @author Akash Yadav
 */
object LspHandler {

  fun registerLanguageServers() {
    ILanguageServerRegistry.getDefault().apply {
    //  getServer(JavaLanguageServer.SERVER_ID) ?: register(JavaLanguageServer())
    //  getServer(XMLLanguageServer.SERVER_ID) ?: register(XMLLanguageServer())
    getServer(KotlinLanguageServer.SERVER_ID) ?: register(KotlinLanguageServer()) 
    }
  }
  
  fun connectClient(client: ILanguageClient) {
    ILanguageServerRegistry.getDefault().connectClient(client)
  }

  fun destroyLanguageServers(isConfigurationChange: Boolean) {
    if (isConfigurationChange) {
      return
    }
    ILanguageServerRegistry.getDefault().destroy()
  }
}

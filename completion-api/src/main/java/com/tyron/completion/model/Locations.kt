package com.tyron.completion.model

import com.google.gson.annotations.SerializedName
import java.nio.file.Path
import com.tyron.completion.model.Range

data class Location(var file: Path, var range: Range)
package datadog.trace.api.naming.v1;

import datadog.trace.api.naming.NamingSchema;
import javax.annotation.Nonnull;

public class DatabaseNamingV1 implements NamingSchema.ForDatabase {
  @Override
  public String normalizedName(@Nonnull String rawName) {
    switch (rawName) {
      case "mongo":
        return "mongodb";
      case "sqlserver":
        return "mssql";
    }
    return rawName;
  }

  @Nonnull
  @Override
  public String operation(@Nonnull String databaseType) {
    if ("elasticsearch.rest".equals(databaseType)) {
      return "elasticsearch.query";
    }
    // already normalized when calling dbType on the decorator. It saves one operation
    return databaseType + ".query";
  }

  @Nonnull
  @Override
  public String service(@Nonnull String ddService, @Nonnull String databaseType) {
    return ddService;
  }
}

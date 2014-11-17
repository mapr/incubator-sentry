/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sentry.binding.hive;

import org.apache.commons.lang.StringUtils;
import static org.apache.hadoop.hive.metastore.MetaStoreUtils.DEFAULT_DATABASE_NAME;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.util.*;

import org.apache.hadoop.hive.common.JavaUtils;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.conf.HiveConf.ConfVars;
import org.apache.hadoop.hive.ql.HiveDriverFilterHook;
import org.apache.hadoop.hive.ql.HiveDriverFilterHookContext;
import org.apache.hadoop.hive.ql.HiveDriverFilterHookResult;
import org.apache.hadoop.hive.ql.HiveDriverFilterHookResultImpl;
import org.apache.hadoop.hive.ql.exec.SentryGrantRevokeTask;
import org.apache.hadoop.hive.ql.exec.Task;
import org.apache.hadoop.hive.ql.hooks.Entity;
import org.apache.hadoop.hive.ql.hooks.Entity.Type;
import org.apache.hadoop.hive.ql.hooks.Hook;
import org.apache.hadoop.hive.ql.hooks.ReadEntity;
import org.apache.hadoop.hive.ql.hooks.WriteEntity;
import org.apache.hadoop.hive.ql.lib.Node;
import org.apache.hadoop.hive.ql.metadata.AuthorizationException;
import org.apache.hadoop.hive.ql.parse.ASTNode;
import org.apache.hadoop.hive.ql.parse.AbstractSemanticAnalyzerHook;
import org.apache.hadoop.hive.ql.parse.BaseSemanticAnalyzer;
import org.apache.hadoop.hive.ql.parse.HiveParser;
import org.apache.hadoop.hive.ql.parse.HiveSemanticAnalyzerHookContext;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.ql.plan.HiveOperation;
import org.apache.hadoop.hive.ql.session.SessionState;
import org.apache.sentry.binding.hive.authz.HiveAuthzBinding;
import org.apache.sentry.binding.hive.authz.HiveAuthzPrivileges;
import org.apache.sentry.binding.hive.authz.HiveAuthzPrivileges.HiveOperationScope;
import org.apache.sentry.binding.hive.authz.HiveAuthzPrivileges.HiveOperationType;
import org.apache.sentry.binding.hive.authz.HiveAuthzPrivilegesMap;
import org.apache.sentry.binding.hive.conf.HiveAuthzConf;
import org.apache.sentry.core.common.Subject;
import org.apache.sentry.core.common.utils.PathUtils;
import org.apache.sentry.core.model.db.AccessURI;
import org.apache.sentry.core.model.db.DBModelAction;
import org.apache.sentry.core.model.db.DBModelAuthorizable;
import org.apache.sentry.core.model.db.DBModelAuthorizable.AuthorizableType;
import org.apache.sentry.core.model.db.Database;
import org.apache.sentry.core.model.db.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class HiveAuthzBindingHook extends AbstractSemanticAnalyzerHook
implements HiveDriverFilterHook {
  private static final Logger LOG = LoggerFactory
      .getLogger(HiveAuthzBindingHook.class);
  private final HiveAuthzBinding hiveAuthzBinding;
  private final HiveAuthzConf authzConf;
  private Database currDB = Database.ALL;
  private Table currTab;
  private AccessURI udfURI;
  private AccessURI partitionURI;

  public HiveAuthzBindingHook() throws Exception {
    SessionState session = SessionState.get();
    if(session == null) {
      throw new IllegalStateException("Session has not been started");
    }
    HiveConf hiveConf = session.getConf();
    if(hiveConf == null) {
      throw new IllegalStateException("Session HiveConf is null");
    }
    authzConf = loadAuthzConf(hiveConf);
    hiveAuthzBinding = new HiveAuthzBinding(hiveConf, authzConf);
  }

  public static HiveAuthzConf loadAuthzConf(HiveConf hiveConf) {
    boolean depreicatedConfigFile = false;
    HiveAuthzConf newAuthzConf = null;
    String hiveAuthzConf = hiveConf.get(HiveAuthzConf.HIVE_SENTRY_CONF_URL);
    if(hiveAuthzConf == null || (hiveAuthzConf = hiveAuthzConf.trim()).isEmpty()) {
      hiveAuthzConf = hiveConf.get(HiveAuthzConf.HIVE_ACCESS_CONF_URL);
      depreicatedConfigFile = true;
    }

    if(hiveAuthzConf == null || (hiveAuthzConf = hiveAuthzConf.trim()).isEmpty()) {
      throw new IllegalArgumentException("Configuration key " + HiveAuthzConf.HIVE_SENTRY_CONF_URL
          + " value '" + hiveAuthzConf + "' is invalid.");
    }
    try {
      newAuthzConf = new HiveAuthzConf(new URL(hiveAuthzConf));
    } catch (MalformedURLException e) {
      if (depreicatedConfigFile) {
        throw new IllegalArgumentException("Configuration key " + HiveAuthzConf.HIVE_ACCESS_CONF_URL
            + " specifies a malformed URL '" + hiveAuthzConf + "'", e);
      } else {
        throw new IllegalArgumentException("Configuration key " + HiveAuthzConf.HIVE_SENTRY_CONF_URL
            + " specifies a malformed URL '" + hiveAuthzConf + "'", e);
      }
    }
    return newAuthzConf;
  }

  /**
   * Pre-analyze hook called after compilation and before semantic analysis We
   * extract things for to Database and metadata level operations which are not
   * capture in the input/output entities during semantic analysis. Ideally it
   * should be handled in Hive. We need to move most of these into hive semantic
   * analyzer and then remove it from the access hook.
   */
  @Override
  public ASTNode preAnalyze(HiveSemanticAnalyzerHookContext context, ASTNode ast)
      throws SemanticException {

    switch (ast.getToken().getType()) {
    // Hive parser doesn't capture the database name in output entity, so we store it here for now
      case HiveParser.TOK_CREATEDATABASE:
      case HiveParser.TOK_ALTERDATABASE_PROPERTIES:
      case HiveParser.TOK_DROPDATABASE:
      case HiveParser.TOK_SWITCHDATABASE:
      case HiveParser.TOK_DESCDATABASE:
        currDB = new Database(BaseSemanticAnalyzer.unescapeIdentifier(ast.getChild(0).getText()));
        break;
      case HiveParser.TOK_CREATETABLE:
      case HiveParser.TOK_CREATEVIEW:
        /*
         * Compiler doesn't create read/write entities for create table.
         * Hence we need extract dbname from db.tab format, if applicable
         */
        currDB = extractDatabase((ASTNode)ast.getChild(0));
        break;
      case HiveParser.TOK_DROPTABLE:
      case HiveParser.TOK_DROPVIEW:
      case HiveParser.TOK_SHOW_TABLESTATUS:
      case HiveParser.TOK_SHOW_CREATETABLE:
      case HiveParser.TOK_ALTERTABLE_SERIALIZER:
      case HiveParser.TOK_ALTERVIEW_ADDPARTS:
      case HiveParser.TOK_ALTERVIEW_DROPPARTS:
      case HiveParser.TOK_ALTERVIEW_PROPERTIES:
      case HiveParser.TOK_ALTERVIEW_RENAME:
      case HiveParser.TOK_CREATEINDEX:
      case HiveParser.TOK_DROPINDEX:
        currTab = extractTable((ASTNode)ast.getFirstChildWithType(HiveParser.TOK_TABNAME));
        currDB = extractDatabase((ASTNode) ast.getChild(0));
        break;
      case HiveParser.TOK_ALTERTABLE_RENAME:
      case HiveParser.TOK_ALTERTABLE_PROPERTIES:
      case HiveParser.TOK_ALTERTABLE_DROPPARTS:
      case HiveParser.TOK_ALTERTABLE_RENAMECOL:
      case HiveParser.TOK_ALTERTABLE_ADDCOLS:
      case HiveParser.TOK_ALTERTABLE_REPLACECOLS:
      case HiveParser.TOK_SHOW_TBLPROPERTIES:
      case HiveParser.TOK_SHOWINDEXES:
      case HiveParser.TOK_SHOWPARTITIONS:
        //token name TOK_TABNAME is not properly set in this case
        currTab = extractTable((ASTNode)ast.getChild(0));
        currDB = extractDatabase((ASTNode)ast.getChild(0));
        break;
      case HiveParser.TOK_ALTERTABLE_ADDPARTS:
        /*
         * Compiler doesn't create read/write entities for create table.
         * Hence we need extract dbname from db.tab format, if applicable
         */
        currTab = extractTable((ASTNode)ast.getChild(0));
        currDB = extractDatabase((ASTNode)ast.getChild(0));
        partitionURI = extractPartition(ast);
        break;
      case HiveParser.TOK_CREATEFUNCTION:
        String udfClassName = BaseSemanticAnalyzer.unescapeSQLString(ast.getChild(1).getText());
        try {
          CodeSource udfSrc = Class.forName(udfClassName).getProtectionDomain().getCodeSource();
          if (udfSrc == null) {
            throw new SemanticException("Could not resolve the jar for UDF class " + udfClassName);
          }
          String udfJar = udfSrc.getLocation().getPath();
          if (udfJar == null || udfJar.isEmpty()) {
            throw new SemanticException("Could not find the jar for UDF class " + udfClassName +
                "to validate privileges");
          }
          udfURI = parseURI(udfSrc.getLocation().toString(), true);
        } catch (ClassNotFoundException e) {
          throw new SemanticException("Error retrieving udf class", e);
        }
        // create/drop function is allowed with any database
        currDB = Database.ALL;
        break;
      case HiveParser.TOK_DROPFUNCTION:
        // create/drop function is allowed with any database
        currDB = Database.ALL;
        break;

      case HiveParser.TOK_LOAD:
        String dbName = BaseSemanticAnalyzer.unescapeIdentifier(ast.getChild(1).getChild(0).getChild(0).getText());
        currDB = new Database(dbName);
        break;
      default:
        currDB = getCanonicalDb();
        break;
    }
    return ast;
  }

  // Find the current database for session
  private Database getCanonicalDb() {
    return new Database(SessionState.get().getCurrentDatabase());
  }

  private Database extractDatabase(ASTNode ast) throws SemanticException {
    String tableName = BaseSemanticAnalyzer.getUnescapedName(ast);
    if (tableName.contains(".")) {
      return new Database((tableName.split("\\."))[0]);
    } else {
      return getCanonicalDb();
    }
  }
  private Table extractTable(ASTNode ast) throws SemanticException {
    String tableName = BaseSemanticAnalyzer.getUnescapedName(ast);
    if (tableName.contains(".")) {
      return new Table((tableName.split("\\."))[1]);
    } else {
      return new Table(tableName);
    }
  }

  @VisibleForTesting
  protected static AccessURI extractPartition(ASTNode ast) throws SemanticException {
    for (int i = 0; i < ast.getChildCount(); i++) {
      ASTNode child = (ASTNode)ast.getChild(i);
      if (child.getToken().getType() == HiveParser.TOK_PARTITIONLOCATION &&
          child.getChildCount() == 1) {
        return parseURI(BaseSemanticAnalyzer.
          unescapeSQLString(child.getChild(0).getText()));
      }
    }
    return null;
  }

  @VisibleForTesting
  protected static AccessURI parseURI(String uri) throws SemanticException {
    return parseURI(uri, false);
  }

  @VisibleForTesting
  protected static AccessURI parseURI(String uri, boolean isLocal)
      throws SemanticException {
    try {
      HiveConf conf = SessionState.get().getConf();
      String warehouseDir = conf.getVar(ConfVars.METASTOREWAREHOUSE);
      return new AccessURI(PathUtils.parseDFSURI(warehouseDir, uri, isLocal));
    } catch (Exception e) {
      throw new SemanticException("Error parsing URI " + uri + ": " +
        e.getMessage(), e);
    }
  }

  /**
   * Post analyze hook that invokes hive auth bindings
   */
  @Override
  public void postAnalyze(HiveSemanticAnalyzerHookContext context,
      List<Task<? extends Serializable>> rootTasks) throws SemanticException {
    HiveOperation stmtOperation = getCurrentHiveStmtOp();
    HiveAuthzPrivileges stmtAuthObject;

    stmtAuthObject = HiveAuthzPrivilegesMap.getHiveAuthzPrivileges(stmtOperation);

    // must occur above the null check on stmtAuthObject
    // since GRANT/REVOKE/etc are not authorized by binding layer at present
    Subject subject = getCurrentSubject(context);
    Set<String> subjectGroups = hiveAuthzBinding.getGroups(subject);
    for (Task<? extends Serializable> task : rootTasks) {
      if (task instanceof SentryGrantRevokeTask) {
        SentryGrantRevokeTask sentryTask = (SentryGrantRevokeTask)task;
        sentryTask.setHiveAuthzBinding(hiveAuthzBinding);
        sentryTask.setAuthzConf(authzConf);
        sentryTask.setSubject(subject);
        sentryTask.setSubjectGroups(subjectGroups);
        sentryTask.setIpAddress(context.getIpAddress());
        sentryTask.setOperation(stmtOperation);
      }
    }

    if (stmtAuthObject == null) {
      // We don't handle authorizing this statement
      return;
    }
    try {
      authorizeWithHiveBindings(context, stmtAuthObject, stmtOperation);
    } catch (AuthorizationException e) {
      executeOnFailureHooks(context, stmtOperation, e);
      String permsRequired = "";
      for (String perm : hiveAuthzBinding.getLastQueryPrivilegeErrors()) {
        permsRequired += perm + ";";
      }
      context.getConf().set(HiveAuthzConf.HIVE_SENTRY_AUTH_ERRORS, permsRequired);
      throw new SemanticException(HiveAuthzConf.HIVE_SENTRY_PRIVILEGE_ERROR_MESSAGE, e);
    }
    if ("true".equalsIgnoreCase(context.getConf().
        get(HiveAuthzConf.HIVE_SENTRY_MOCK_COMPILATION))) {
      throw new SemanticException(HiveAuthzConf.HIVE_SENTRY_MOCK_ERROR + " Mock query compilation aborted. Set " +
          HiveAuthzConf.HIVE_SENTRY_MOCK_COMPILATION + " to 'false' for normal query processing");
    }
    hiveAuthzBinding.set(context.getConf());
  }

  private void executeOnFailureHooks(HiveSemanticAnalyzerHookContext context,
      HiveOperation hiveOp, AuthorizationException e) {
    SentryOnFailureHookContext hookCtx = new SentryOnFailureHookContextImpl(
        context.getCommand(), context.getInputs(), context.getOutputs(),
        hiveOp, currDB, currTab, udfURI, partitionURI, context.getUserName(),
        context.getIpAddress(), e, context.getConf());
    String csHooks = authzConf.get(
        HiveAuthzConf.AuthzConfVars.AUTHZ_ONFAILURE_HOOKS.getVar(), "").trim();

    try {
      for (Hook aofh : getHooks(csHooks)) {
        ((SentryOnFailureHook)aofh).run(hookCtx);
      }
    } catch (Exception ex) {
      LOG.error("Error executing hook:", ex);
    }
  }

  public static void runFailureHook(SentryOnFailureHookContext hookContext,
      String csHooks) {
    try {
      for (Hook aofh : getHooks(csHooks)) {
        ((SentryOnFailureHook) aofh).run(hookContext);
      }
    } catch (Exception ex) {
      LOG.error("Error executing hook:", ex);
    }
  }
  /**
   * Convert the input/output entities into authorizables. generate
   * authorizables for cases like Database and metadata operations where the
   * compiler doesn't capture entities. invoke the hive binding to validate
   * permissions
   *
   * @param context
   * @param stmtAuthObject
   * @param stmtOperation
   * @throws AuthorizationException
   */
  private void authorizeWithHiveBindings(HiveSemanticAnalyzerHookContext context,
      HiveAuthzPrivileges stmtAuthObject, HiveOperation stmtOperation) throws  AuthorizationException {
    Set<ReadEntity> inputs = context.getInputs();
    Set<WriteEntity> outputs = context.getOutputs();
    List<List<DBModelAuthorizable>> inputHierarchy = new ArrayList<List<DBModelAuthorizable>>();
    List<List<DBModelAuthorizable>> outputHierarchy = new ArrayList<List<DBModelAuthorizable>>();

    if(LOG.isDebugEnabled()) {
      LOG.debug("stmtAuthObject.getOperationScope() = " + stmtAuthObject.getOperationScope());
      LOG.debug("context.getInputs() = " + context.getInputs());
      LOG.debug("context.getOutputs() = " + context.getOutputs());
    }

    switch (stmtAuthObject.getOperationScope()) {

    case SERVER :
      // validate server level privileges if applicable. Eg create UDF,register jar etc ..
      List<DBModelAuthorizable> serverHierarchy = new ArrayList<DBModelAuthorizable>();
      serverHierarchy.add(hiveAuthzBinding.getAuthServer());
      inputHierarchy.add(serverHierarchy);
      break;
    case DATABASE:
      // workaround for database scope statements (create/alter/drop db)
      List<DBModelAuthorizable> dbHierarchy = new ArrayList<DBModelAuthorizable>();
      dbHierarchy.add(hiveAuthzBinding.getAuthServer());
      dbHierarchy.add(currDB);
      inputHierarchy.add(dbHierarchy);
      outputHierarchy.add(dbHierarchy);

      for(ReadEntity readEntity:inputs) {
        // skip the tables/view that are part of expanded view definition.
        if (isChildTabForView(readEntity)) {
          continue;
        }
        // If this is a UDF, then check whether its allowed to be executed
         // TODO: when we support execute privileges on UDF, this can be removed.
        if (isUDF(readEntity)) {
          if (isBuiltinUDF(readEntity)) {
            checkUDFWhiteList(readEntity.getUDF().getDisplayName());
          }
          continue;
        }
        List<DBModelAuthorizable> entityHierarchy = new ArrayList<DBModelAuthorizable>();
        entityHierarchy.add(hiveAuthzBinding.getAuthServer());
        entityHierarchy.addAll(getAuthzHierarchyFromEntity(readEntity));
        inputHierarchy.add(entityHierarchy);
      }
      break;
    case TABLE:
      // workaround for add partitions
      if(partitionURI != null) {
        inputHierarchy.add(ImmutableList.of(hiveAuthzBinding.getAuthServer(), partitionURI));
      }

      for (ReadEntity readEntity: inputs) {
        // skip the tables/view that are part of expanded view definition.
        if (isChildTabForView(readEntity)) {
          continue;
        }
        // If this is a UDF, then check whether its allowed to be executed
        // TODO: when we support execute privileges on UDF, this can be removed.
        if (isUDF(readEntity)) {
          if (isBuiltinUDF(readEntity)) {
            checkUDFWhiteList(readEntity.getUDF().getDisplayName());
          }
          continue;
        }
        List<DBModelAuthorizable> entityHierarchy = new ArrayList<DBModelAuthorizable>();
        entityHierarchy.add(hiveAuthzBinding.getAuthServer());
        entityHierarchy.addAll(getAuthzHierarchyFromEntity(readEntity));
        inputHierarchy.add(entityHierarchy);
      }
      for (WriteEntity writeEntity: outputs) {
        if (filterWriteEntity(writeEntity)) {
          continue;
        }
        List<DBModelAuthorizable> entityHierarchy = new ArrayList<DBModelAuthorizable>();
        entityHierarchy.add(hiveAuthzBinding.getAuthServer());
        entityHierarchy.addAll(getAuthzHierarchyFromEntity(writeEntity));
        outputHierarchy.add(entityHierarchy);
      }
      // workaround for metadata queries.
      // Capture the table name in pre-analyze and include that in the entity list
      if (currTab != null) {
        List<DBModelAuthorizable> externalAuthorizableHierarchy = new ArrayList<DBModelAuthorizable>();
        externalAuthorizableHierarchy.add(hiveAuthzBinding.getAuthServer());
        externalAuthorizableHierarchy.add(currDB);
        externalAuthorizableHierarchy.add(currTab);
        inputHierarchy.add(externalAuthorizableHierarchy);
      }
      break;
    case CONNECT:
      /* The 'CONNECT' is an implicit privilege scope currently used for
       *  - CREATE TEMP FUNCTION
       *  - DROP TEMP FUNCTION
       *  - USE <db>
       *  It's allowed when the user has any privilege on the current database. For application
       *  backward compatibility, we allow (optional) implicit connect permission on 'default' db.
       */
      List<DBModelAuthorizable> connectHierarchy = new ArrayList<DBModelAuthorizable>();
      connectHierarchy.add(hiveAuthzBinding.getAuthServer());
      // by default allow connect access to default db
      Table currTbl = Table.ALL;
      if ((DEFAULT_DATABASE_NAME.equalsIgnoreCase(currDB.getName()) &&
          "false".equalsIgnoreCase(authzConf.
              get(HiveAuthzConf.AuthzConfVars.AUTHZ_RESTRICT_DEFAULT_DB.getVar(), "false")))
              ||stmtOperation.equals(HiveOperation.CREATEFUNCTION)
              ||stmtOperation.equals(HiveOperation.DROPFUNCTION)) {
        currDB = Database.ALL;
        currTbl = Table.SOME;
      }

      connectHierarchy.add(currDB);
      connectHierarchy.add(currTbl);

      inputHierarchy.add(connectHierarchy);
      // check if this is a create temp function and we need to validate URI
      if (udfURI != null) {
        List<DBModelAuthorizable> udfUriHierarchy = new ArrayList<DBModelAuthorizable>();
        udfUriHierarchy.add(hiveAuthzBinding.getAuthServer());
        udfUriHierarchy.add(udfURI);
        inputHierarchy.add(udfUriHierarchy);
      }

      outputHierarchy.add(connectHierarchy);
      break;

    default:
      throw new AuthorizationException("Unknown operation scope type " +
          stmtAuthObject.getOperationScope().toString());
    }

    // validate permission
    hiveAuthzBinding.authorize(stmtOperation, stmtAuthObject, getCurrentSubject(context),
        inputHierarchy, outputHierarchy);

    hiveAuthzBinding.set(context.getConf());
  }

  private boolean isUDF(ReadEntity readEntity) {
    return readEntity.getType().equals(Type.UDF);
  }

  private boolean isBuiltinUDF(ReadEntity readEntity) {
    return readEntity.getType().equals(Type.UDF) &&
        readEntity.getUDF().isNative();

  }

  private void checkUDFWhiteList(String queryUDF) throws AuthorizationException {
    String whiteList = authzConf.get(HiveAuthzConf.AuthzConfVars.AUTHZ_UDF_WHITELIST.getVar());
    if (whiteList == null) {
      return;
    }
    for (String hiveUDF : Splitter.on(",").omitEmptyStrings().trimResults().split(whiteList)) {
      if (queryUDF.equalsIgnoreCase(hiveUDF)) {
        return; // found the given UDF in whitelist
      }
    }
    throw new AuthorizationException("The UDF " + queryUDF + " is not found in the list of allowed UDFs");
  }

  private HiveOperation getCurrentHiveStmtOp() {
    SessionState sessState = SessionState.get();
    if (sessState == null) {
      // TODO: Warn
      return null;
    }
    return sessState.getHiveOperation();
  }

  private Subject getCurrentSubject(HiveSemanticAnalyzerHookContext context) {
    // Extract the username from the hook context
    return new Subject(context.getUserName());
  }

  // Build the hierarchy of authorizable object for the given entity type.
  private List<DBModelAuthorizable> getAuthzHierarchyFromEntity(Entity entity) {
    List<DBModelAuthorizable> objectHierarchy = new ArrayList<DBModelAuthorizable>();
    switch (entity.getType()) {
    case TABLE:
      objectHierarchy.add(new Database(entity.getTable().getDbName()));
      objectHierarchy.add(new Table(entity.getTable().getTableName()));
      break;
    case PARTITION:
      objectHierarchy.add(new Database(entity.getPartition().getTable().getDbName()));
      objectHierarchy.add(new Table(entity.getPartition().getTable().getTableName()));
      break;
    case DFS_DIR:
    case LOCAL_DIR:
      try {
        objectHierarchy.add(parseURI(entity.toString()));
      } catch (Exception e) {
        throw new AuthorizationException("Failed to get File URI", e);
      }
      break;
    case DATABASE:
      // TODO use database entities from compiler instead of capturing from AST
      break;
    default:
      throw new UnsupportedOperationException("Unsupported entity type " +
          entity.getType().name());
    }
    return objectHierarchy;
  }

  // Check if this write entity needs to skipped
  private boolean filterWriteEntity(WriteEntity writeEntity)
      throws AuthorizationException {
    // skip URI validation for session scratch file URIs
    try {
      if (writeEntity.getTyp().equals(Type.DFS_DIR)
          || writeEntity.getTyp().equals(Type.LOCAL_DIR)) {
        HiveConf conf = SessionState.get().getConf();
        String warehouseDir = conf.getVar(ConfVars.METASTOREWAREHOUSE);
        URI scratchURI = new URI(PathUtils.parseDFSURI(warehouseDir,
          conf.getVar(HiveConf.ConfVars.SCRATCHDIR)));
        URI requestURI = new URI(PathUtils.parseDFSURI(warehouseDir,
          writeEntity.getLocation().getPath()));
        LOG.debug("scratchURI = " + scratchURI + ", requestURI = " + requestURI);
        if (PathUtils.impliesURI(scratchURI, requestURI)) {
          return true;
        }
        URI localScratchURI = new URI(PathUtils.parseLocalURI(conf.getVar(HiveConf.ConfVars.LOCALSCRATCHDIR)));
        URI localRequestURI = new URI(PathUtils.parseLocalURI(writeEntity.getLocation().getPath()));
        LOG.debug("localScratchURI = " + localScratchURI + ", localRequestURI = " + localRequestURI);
        if (PathUtils.impliesURI(localScratchURI, localRequestURI)) {
          return true;
        }
      }
    } catch (Exception e) {
      throw new AuthorizationException("Failed to extract uri details", e);
    }
    return false;
  }

  private List<String> filterShowTables(List<String> queryResult,
      HiveOperation operation, String userName, String dbName)
          throws SemanticException {
    List<String> filteredResult = new ArrayList<String>();
    Subject subject = new Subject(userName);
    HiveAuthzPrivileges tableMetaDataPrivilege = new HiveAuthzPrivileges.AuthzPrivilegeBuilder().
        addInputObjectPriviledge(AuthorizableType.Table, EnumSet.of(DBModelAction.SELECT, DBModelAction.INSERT)).
        setOperationScope(HiveOperationScope.TABLE).
        setOperationType(HiveOperationType.INFO).
        build();

    Iterator queryResultIterator = queryResult.iterator();

    while (queryResultIterator.hasNext()) {
      Object queryResultItem = (Object)queryResultIterator.next();
      Object tableNameArray[] = (Object[]) queryResultItem;

      for (Object tableName : tableNameArray) {
          // if user has privileges on table, add to filtered list, else discard
        Table table = new Table((String)tableName);
        Database database;
        database = new Database(dbName);

        List<List<DBModelAuthorizable>> inputHierarchy = new ArrayList<List<DBModelAuthorizable>>();
        List<List<DBModelAuthorizable>> outputHierarchy = new ArrayList<List<DBModelAuthorizable>>();
        List<DBModelAuthorizable> externalAuthorizableHierarchy = new ArrayList<DBModelAuthorizable>();
        externalAuthorizableHierarchy.add(hiveAuthzBinding.getAuthServer());
        externalAuthorizableHierarchy.add(database);
        externalAuthorizableHierarchy.add(table);
        inputHierarchy.add(externalAuthorizableHierarchy);

        try {
          hiveAuthzBinding.authorize(operation, tableMetaDataPrivilege, subject,
                inputHierarchy, outputHierarchy);
          filteredResult.add(table.getName());
        } catch (AuthorizationException e) {
          // squash the exception, user doesn't have privileges, so the table is
          // not added to
          // filtered list.
          ;
        }
      }
    }
    return filteredResult;
  }

    private List<String> filterShowDatabases(List<String> queryResult,
                                             HiveOperation operation, String userName) throws SemanticException {
      List<String> filteredResult = new ArrayList<String>();
      Subject subject = new Subject(userName);
      HiveAuthzPrivileges anyPrivilege = new HiveAuthzPrivileges.AuthzPrivilegeBuilder().
          addInputObjectPriviledge(AuthorizableType.Table, EnumSet.of(DBModelAction.SELECT, DBModelAction.INSERT)).
          addInputObjectPriviledge(AuthorizableType.URI, EnumSet.of(DBModelAction.SELECT)).
          setOperationScope(HiveOperationScope.CONNECT).
          setOperationType(HiveOperationType.QUERY).
          build();

      Iterator queryResultIterator = queryResult.iterator();

      while (queryResultIterator.hasNext()) {
        Object queryResultItem = (Object)queryResultIterator.next();
        Object dbNameArray[] = (Object[])queryResultItem;

        for (Object dbName : dbNameArray) {
          // if user has privileges on database, add to filtered list, else discard
          Database database = null;

                // if default is not restricted, continue
          if (DEFAULT_DATABASE_NAME.equalsIgnoreCase((String)dbName) &&
              "false".equalsIgnoreCase(authzConf.
              get(HiveAuthzConf.AuthzConfVars.AUTHZ_RESTRICT_DEFAULT_DB.getVar(), "false"))) {
            filteredResult.add(DEFAULT_DATABASE_NAME);
            continue;
          }

          database = new Database((String)dbName);

          List<List<DBModelAuthorizable>> inputHierarchy = new ArrayList<List<DBModelAuthorizable>>();
          List<List<DBModelAuthorizable>> outputHierarchy = new ArrayList<List<DBModelAuthorizable>>();
          List<DBModelAuthorizable> externalAuthorizableHierarchy = new ArrayList<DBModelAuthorizable>();
          externalAuthorizableHierarchy.add(hiveAuthzBinding.getAuthServer());
          externalAuthorizableHierarchy.add(database);
          externalAuthorizableHierarchy.add(Table.ALL);
          inputHierarchy.add(externalAuthorizableHierarchy);

          try {
            hiveAuthzBinding.authorize(operation, anyPrivilege, subject,
            inputHierarchy, outputHierarchy);
            filteredResult.add(database.getName());
          } catch (AuthorizationException e) {
            // squash the exception, user doesn't have privileges, so the table is
            // not added to
            // filtered list.
            ;
          }
        }
      }

      return filteredResult;
    }

  @Override
  public HiveDriverFilterHookResult postDriverFetch( HiveDriverFilterHookContext hookContext)
      throws Exception {
    HiveDriverFilterHookResult hookResult = new HiveDriverFilterHookResultImpl();
    HiveOperation hiveOperation = hookContext.getHiveOperation();
    List<String> queryResult = new ArrayList<String>();
    queryResult = hookContext.getResult();
    List<String> filteredResult = null;
    String userName = hookContext.getUserName();
    String operationName = hiveOperation.getOperationName();

    if ("SHOWTABLES".equalsIgnoreCase(operationName)) {
      filteredResult = filterShowTables(queryResult, hiveOperation, userName,
          hookContext.getDbName());
    } else if ("SHOWDATABASES".equalsIgnoreCase(operationName)) {
      filteredResult = filterShowDatabases(queryResult, hiveOperation, userName);
    }

    hookResult.setHiveOperation(hiveOperation);
    hookResult.setResult(filteredResult);
    hookResult.setUserName(userName);
    hookResult.setConf(hookContext.getConf());


    return hookResult;
  }

  /**
   * Check if the given read entity is a table that has parents of type Table
   * Hive compiler performs a query rewrite by replacing view with its definition. In the process, tt captures both
   * the original view and the tables/view that it selects from .
   * The access authorization is only interested in the top level views and not the underlying tables.
   * @param readEntity
   * @return
   */
  private boolean isChildTabForView(ReadEntity readEntity) {
    // If this is a table added for view, then we need to skip that
    if (!readEntity.getType().equals(Type.TABLE) && !readEntity.getType().equals(Type.PARTITION)) {
      return false;
    }
    if ((readEntity.getParents() != null) && (readEntity.getParents().size() > 0)) {
      for (ReadEntity parentEntity : readEntity.getParents()) {
        if (!parentEntity.getType().equals(Type.TABLE)) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns a set of hooks specified in a configuration variable.
   *
   * See getHooks(HiveAuthzConf.AuthzConfVars hookConfVar, Class<T> clazz)
   * @param hookConfVar
   * @return
   * @throws Exception
   */
  private static List<Hook> getHooks(String csHooks) throws Exception {
    return getHooks(csHooks, Hook.class);
  }

  /**
   * Returns the hooks specified in a configuration variable.  The hooks are returned in a list in
   * the order they were specified in the configuration variable.
   *
   * @param hookConfVar The configuration variable specifying a comma separated list of the hook
   *                    class names.
   * @param clazz       The super type of the hooks.
   * @return            A list of the hooks cast as the type specified in clazz, in the order
   *                    they are listed in the value of hookConfVar
   * @throws Exception
   */
  private static <T extends Hook> List<T> getHooks(String csHooks,
      Class<T> clazz)
      throws Exception {

    List<T> hooks = new ArrayList<T>();
    if (csHooks.isEmpty()) {
      return hooks;
    }
    for (String hookClass : Splitter.on(",").omitEmptyStrings().trimResults().split(csHooks)) {
      try {
        @SuppressWarnings("unchecked")
        T hook =
            (T) Class.forName(hookClass, true, JavaUtils.getClassLoader()).newInstance();
        hooks.add(hook);
      } catch (ClassNotFoundException e) {
        LOG.error(hookClass + " Class not found:" + e.getMessage());
        throw e;
      }
    }

    return hooks;
  }
}

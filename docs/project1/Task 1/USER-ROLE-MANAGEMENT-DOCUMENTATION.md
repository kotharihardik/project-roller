# Apache Roller - User and Role Management Subsystem Documentation

## Overview

The **User and Role Management Subsystem** is a core component of Apache Roller that handles:

- **User lifecycle management**: Creation, modification, deletion, and retrieval of user accounts
- **Authentication**: Multiple authentication methods
- **Authorization**: Role-based and permission-based access control
- **Weblog permissions**: Fine-grained permissions for weblog operations
- **Session management**: User session tracking and invalidation
- **Security enforcement**: Interceptors and filters for access control

This subsystem follows a layered architecture pattern with clear separation between presentation, business logic, and data access layers.

---

## Package Structure

| Package | Purpose |
|---------|---------|
| `org.apache.roller.weblogger.pojos` | Domain entities and permission objects |
| `org.apache.roller.weblogger.business` | Business service interfaces |
| `org.apache.roller.weblogger.business.jpa` | JPA implementations of business services |
| `org.apache.roller.weblogger.ui.core.security` | Spring Security integration components |
| `org.apache.roller.weblogger.ui.struts2.admin` | Admin-only user management actions |
| `org.apache.roller.weblogger.ui.struts2.core` | Core user-facing actions (login, register, profile) |
| `org.apache.roller.weblogger.ui.struts2.editor` | Weblog member management actions |
| `org.apache.roller.weblogger.ui.struts2.util` | Struts2 base classes and interceptors |
| `org.apache.roller.weblogger.ui.core` | Session management and UI beans |
| `org.apache.roller.weblogger.util.cache` | Caching infrastructure |
| `org.apache.roller.weblogger.config` | Configuration enums and utilities |

---

## Domain/Entity Layer (pojos)

### 1. User

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Core entity representing a user account in the system.

**Description:**
The `User` class is the central entity for user management. It stores all user profile information and authentication credentials. This class is persisted to the database via JPA and serves as the primary identity object throughout the application.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier (UUID) |
| `userName` | String | Login username (unique) |
| `password` | String | Hashed password |
| `openIdUrl` | String | OpenID URL for OpenID authentication |
| `screenName` | String | Display name shown publicly |
| `fullName` | String | User's full legal name |
| `emailAddress` | String | Email for notifications |
| `dateCreated` | Date | Account creation timestamp |
| `locale` | String | User's preferred locale |
| `timeZone` | String | User's timezone |
| `enabled` | Boolean | Account active status |
| `activationCode` | String | Email verification code |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `resetPassword(newPassword)` | Securely updates the password with proper hashing |
| `hasGlobalPermission(action)` | Checks if user has a specific global permission |
| `hasGlobalPermission(actions)` | Checks if user has any of the specified permissions |

**Interactions:**
- **UserRole**: One-to-many relationship (a user has multiple roles)
- **WeblogPermission**: One-to-many relationship (a user has permissions on multiple weblogs)
- **Weblog**: Many-to-one as owner/creator
- **UserManager**: Managed by this service for CRUD operations
- **RollerSession**: Stores authenticated user reference

---

### 2. UserRole

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents a role assignment for a user.

**Description:**
The `UserRole` class maps users to their global system roles. Unlike weblog-specific permissions, these roles apply system-wide. Common roles include "admin" for administrators and "editor" for content creators.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `userName` | String | Associated username |
| `role` | String | Role name (e.g., "admin", "editor") |

**Interactions:**
- **User**: Many-to-one relationship
- **JPAUserManagerImpl**: Creates and removes role assignments
- **GlobalPermission**: Role names influence global permission checks

---

### 3. RollerPermission (Abstract)

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Base class for all permission types in the system.

**Description:**
`RollerPermission` extends Java's `Permission` class and provides a common framework for managing action-based permissions. It supports comma-separated action strings and provides utility methods for permission manipulation.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `setActions(actions)` | Sets permissions from comma-separated string |
| `getActions()` | Returns permissions as comma-separated string |
| `getActionsAsList()` | Returns permissions as a List |
| `hasAction(action)` | Checks for a specific action |
| `hasActions(actionsToCheck)` | Checks for multiple actions |
| `addActions(newActions)` | Adds new actions to existing set |
| `removeActions(actionsToRemove)` | Removes specified actions |
| `isEmpty()` | Returns true if no actions assigned |

**Inheritance Hierarchy:**
```
java.security.Permission
        
  RollerPermission (abstract)
                
GlobalPermission  ObjectPermission (abstract)
                         
                  WeblogPermission
```

---

### 4. ObjectPermission (Abstract)

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Base class for permissions tied to specific objects (like weblogs).

**Description:**
`ObjectPermission` extends `RollerPermission` to add object-specific context. It tracks which object the permission applies to, supporting pending invitations and permission workflows.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `userName` | String | User this permission belongs to |
| `objectType` | String | Type of object (e.g., "Weblog") |
| `objectId` | String | ID of the specific object |
| `pending` | boolean | True if invitation not yet accepted |
| `dateCreated` | Date | When permission was created |
| `actions` | String | Comma-separated allowed actions |

**Interactions:**
- **WeblogPermission**: Primary concrete implementation
- **UserManager**: Manages permission lifecycle

---

### 5. GlobalPermission

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents system-wide permissions not tied to any specific object.

**Description:**
`GlobalPermission` handles permissions that apply across the entire application. It defines three permission levels with an implicit hierarchy: admin implies weblog implies login.

**Permission Constants:**

| Constant | Value | Description |
|----------|-------|-------------|
| `LOGIN` | "login" | Basic login capability |
| `WEBLOG` | "weblog" | Ability to create/own weblogs |
| `ADMIN` | "admin" | Full administrative access |

**Permission Hierarchy:**
```
admin  weblog  login
                  
(implies all below)
```

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `GlobalPermission(User)` | Creates permission based on user's roles |
| `GlobalPermission(List<String>)` | Creates permission with specified actions |
| `GlobalPermission(User, List<String>)` | Creates permission for user with actions |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `implies(Permission)` | Checks if this permission implies another |
| `actionImplies(action1, action2)` | Private helper for hierarchy check |

**Interactions:**
- **User**: References user for role-based permission construction
- **UserManager.checkPermission()**: Used to verify access
- **JPAUserManagerImpl**: Creates instances during permission checks
- **UISecurityInterceptor**: Uses for action authorization

---

### 6. Weblog

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents a blog/weblog in the system.

**Description:**
The `Weblog` class represents a blog entity. While not solely part of user management, it's tightly integrated because permissions are granted on a per-weblog basis. Each weblog has an owner (creator) and can have multiple members with different permission levels.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `handle` | String | URL-friendly unique name |
| `name` | String | Display name |
| `tagline` | String | Blog description/subtitle |
| `emailAddress` | String | Contact email |
| `locale` | String | Blog's locale |
| `timeZone` | String | Blog's timezone |
| `visible` | Boolean | Public visibility |
| `active` | Boolean | Active status |
| `dateCreated` | Date | Creation timestamp |
| `lastModified` | Date | Last update timestamp |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getCreator()` | Returns the User who owns this weblog |
| `hasUserPermission(user, action)` | Checks if user has specific permission |
| `hasUserPermissions(user, actions)` | Checks if user has any of the permissions |

**Interactions:**
- **User**: Many-to-one as creator/owner
- **WeblogPermission**: One-to-many for member permissions
- **UserManager**: Permission operations reference weblogs

---

### 7. WeblogPermission

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents a user's permissions on a specific weblog.

**Description:**
`WeblogPermission` is the concrete implementation of `ObjectPermission` for weblog access control. It defines what actions a user can perform on a weblog, supporting three permission levels with implicit hierarchy.

**Permission Constants:**

| Constant | Value | Description |
|----------|-------|-------------|
| `EDIT_DRAFT` | "edit_draft" | Can create/edit draft posts only |
| `POST` | "post" | Can publish posts |
| `ADMIN` | "admin" | Full weblog administration |
| `ALL_ACTIONS` | List | All available actions |

**Permission Hierarchy:**
```
ADMIN  POST  EDIT_DRAFT
                  
(implies all below)
```

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogPermission()` | Default constructor |
| `WeblogPermission(weblog, user, actions: String)` | Full specification with string |
| `WeblogPermission(weblog, user, actions: List)` | Full specification with list |
| `WeblogPermission(weblog, actions: List)` | For permission checking (no user) |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getWeblog()` | Returns the associated Weblog |
| `getUser()` | Returns the associated User |
| `implies(Permission)` | Checks if this permission implies another |

**Interactions:**
- **Weblog**: Many-to-one relationship
- **User**: Many-to-one relationship
- **UserManager**: CRUD operations for permissions
- **Members/MembersInvite**: UI for managing permissions
- **UISecurityInterceptor**: Checks weblog permissions for actions

---

## Business Layer

### 8. UserManager (Interface)

**Package:** `org.apache.roller.weblogger.business`

**Role:** Primary business service interface for user and permission management.

**Description:**
`UserManager` defines the contract for all user-related operations. It follows the Service Layer pattern, providing a clean API that abstracts away persistence details. This interface is the main entry point for user operations throughout the application.

**Method Categories:**

#### User CRUD Operations

| Method | Description |
|--------|-------------|
| `addUser(newUser)` | Creates a new user account |
| `saveUser(user)` | Updates existing user |
| `removeUser(user)` | Deletes user and all associated data |
| `getUser(id)` | Retrieves user by ID |
| `getUserByUserName(userName)` | Retrieves user by username |
| `getUserByUserName(userName, enabled)` | Retrieves with enabled filter |
| `getUserByOpenIdUrl(openIdUrl)` | Retrieves by OpenID URL |
| `getUserByActivationCode(code)` | Retrieves by activation code |
| `getUserCount()` | Returns total user count |

#### User Search Operations

| Method | Description |
|--------|-------------|
| `getUsers(enabled, startDate, endDate, offset, length)` | Paginated user list with filters |
| `getUsersStartingWith(startsWith, enabled, offset, length)` | Username prefix search |
| `getUserNameLetterMap()` | Returns count per first letter |
| `getUsersByLetter(letter, offset, length)` | Users starting with letter |

#### Permission Operations

| Method | Description |
|--------|-------------|
| `checkPermission(perm, user)` | Verifies user has permission |
| `grantWeblogPermission(weblog, user, actions)` | Grants immediate permission |
| `grantWeblogPermissionPending(weblog, user, actions)` | Creates pending invitation |
| `confirmWeblogPermission(weblog, user)` | Accepts pending invitation |
| `declineWeblogPermission(weblog, user)` | Rejects pending invitation |
| `revokeWeblogPermission(weblog, user, actions)` | Removes specific permissions |
| `getWeblogPermission(weblog, user)` | Gets user's permission on weblog |
| `getWeblogPermissions(user)` | Gets all user's permissions |
| `getWeblogPermissions(weblog)` | Gets all permissions on weblog |
| `getPendingWeblogPermissions(user)` | Gets user's pending invitations |
| `getPendingWeblogPermissions(weblog)` | Gets weblog's pending invitations |
| `getWeblogPermissionIncludingPending(weblog, user)` | Gets permission including pending |

#### Role Operations

| Method | Description |
|--------|-------------|
| `grantRole(roleName, user)` | Assigns global role to user |
| `revokeRole(roleName, user)` | Removes global role from user |
| `hasRole(roleName, user)` | Checks if user has role |
| `getRoles(user)` | Gets all user's roles |

#### Lifecycle

| Method | Description |
|--------|-------------|
| `release()` | Releases resources |

**Interactions:**
- **JPAUserManagerImpl**: Implementation class
- **All UI Actions**: Use UserManager for user operations
- **RollerUserDetailsService**: Uses for authentication
- **Security components**: Use for permission checks

---

## Persistence Layer (JPA)

### 9. JPAUserManagerImpl

**Package:** `org.apache.roller.weblogger.business.jpa`

**Role:** JPA-based implementation of UserManager interface.

**Description:**
`JPAUserManagerImpl` provides the concrete implementation of `UserManager` using JPA for database operations. It uses `JPAPersistenceStrategy` for all database interactions and maintains a cache for username-to-ID lookups.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `strategy` | JPAPersistenceStrategy | Database access strategy |
| `userNameToIdMap` | Map<String, String> | Cache for fast username lookups |

**Implementation Highlights:**

1. **User Operations:**
   - Uses named JPA queries for efficient database access
   - Maintains username-to-ID cache for performance
   - Handles cascade deletion of related entities

2. **Permission Checking:**
   - Creates `GlobalPermission` for global checks
   - Loads `WeblogPermission` from database for weblog checks
   - Supports permission hierarchy (admin > post > edit_draft)

3. **Role Management:**
   - Creates/deletes `UserRole` entities
   - Maps roles to Spring Security authorities

**Interactions:**
- **JPAPersistenceStrategy**: Delegates all database operations
- **User, UserRole, WeblogPermission**: Manages these entities
- **GlobalPermission**: Creates for permission verification
- **Named Queries**: Uses JPA named queries defined in entity mappings

---

### 10. JPAPersistenceStrategy

**Package:** `org.apache.roller.weblogger.business.jpa`

**Role:** Low-level JPA database operations wrapper.

**Description:**
`JPAPersistenceStrategy` provides a clean abstraction over JPA EntityManager operations. It handles entity lifecycle, query execution, and transaction management.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `store(obj)` | Persists or merges an entity |
| `remove(clazz, id)` | Removes entity by class and ID |
| `remove(obj)` | Removes a specific entity |
| `removeAll(collection)` | Batch removes entities |
| `load(clazz, id)` | Loads entity by ID |
| `refresh(obj)` | Refreshes entity from database |
| `flush()` | Flushes pending changes |
| `release()` | Releases EntityManager |

**Query Methods:**

| Method | Description |
|--------|-------------|
| `getNamedQuery(name, clazz)` | Returns typed named query |
| `getNamedQuery(name)` | Returns untyped named query |
| `getNamedQueryCommitFirst(name, clazz)` | Flushes before query |
| `getDynamicQuery(queryString, clazz)` | Creates typed dynamic query |
| `getDynamicQuery(queryString)` | Creates untyped dynamic query |
| `getNamedUpdate(name)` | Returns update/delete query |

**Interactions:**
- **JPAUserManagerImpl**: Primary consumer
- **TypedQuery/Query**: Returns JPA query objects
- **All JPA entity classes**: Manages persistence

---

## Security Layer

### 11. RollerUserDetailsService

**Package:** `org.apache.roller.weblogger.ui.core.security`

**Role:** Spring Security UserDetailsService implementation for authentication.

**Description:**
`RollerUserDetailsService` bridges Apache Roller's user system with Spring Security. It loads user information during authentication and converts Roller roles to Spring Security authorities.

**Interfaces Implemented:**
- `UserDetailsService` (Spring Security)

**Key Methods:**

| Method | Description |
|--------|-------------|
| `loadUserByUsername(userName)` | Loads user for Spring Security authentication |
| `getAuthorities(userData, umgr)` | Converts Roller roles to Spring authorities |

**Authentication Flow:**

```
1. User submits credentials
2. Spring Security calls loadUserByUsername()
3. Service fetches User via UserManager
4. If not found and AutoProvision enabled, creates user
5. Converts to UserDetails with authorities
6. Returns RollerUserDetails implementation
```

**Interactions:**
- **UserManager**: Fetches user data
- **User**: Source of user information
- **AutoProvision**: For auto-creating users (LDAP, CMA)
- **RollerUserDetails**: Returns this interface implementation
- **SimpleGrantedAuthority**: Creates for Spring Security

---

### 12. RollerUserDetails (Interface)

**Package:** `org.apache.roller.weblogger.ui.core.security`

**Role:** Extended UserDetails with Roller-specific user information.

**Description:**
`RollerUserDetails` extends Spring Security's `UserDetails` interface to include additional user profile information needed by Roller's UI.

**Methods:**

| Method | Description |
|--------|-------------|
| `getTimeZone()` | User's preferred timezone |
| `getLocale()` | User's preferred locale |
| `getScreenName()` | User's display name |
| `getFullName()` | User's full name |
| `getEmailAddress()` | User's email address |

**Interactions:**
- **RollerUserDetailsService**: Creates implementations
- **CustomUserRegistry**: Type-checks against this interface
- **Spring Security**: Used in security context

---

### 13. AutoProvision (Interface)

**Package:** `org.apache.roller.weblogger.ui.core.security`

**Role:** Interface for auto-provisioning users from external sources.

**Description:**
`AutoProvision` defines the contract for automatically creating Roller user accounts when users authenticate through external systems (LDAP, CMA).

**Method:**

| Method | Description |
|--------|-------------|
| `execute(HttpServletRequest)` | Attempts to create user from request context |

**Implementations:**
- `BasicUserAutoProvision`: Default implementation

**Interactions:**
- **RollerUserDetailsService**: Calls during authentication
- **BasicUserAutoProvision**: Concrete implementation
- **HttpServletRequest**: Source of user attributes

---

### 14. BasicUserAutoProvision

**Package:** `org.apache.roller.weblogger.ui.core.security`

**Role:** Default implementation for auto-provisioning users.

**Description:**
`BasicUserAutoProvision` creates new Roller user accounts from authentication context attributes. Used primarily with CMA and LDAP authentication where user attributes are provided by the container or directory.

**Methods:**

| Method | Description |
|--------|-------------|
| `execute(request)` | Creates user from request attributes |
| `hasNecessaryFields(user)` | Validates required fields present |

**Provisioning Flow:**

```
1. Extract user attributes from request/security context
2. Create new User object
3. Populate required fields (username, email, etc.)
4. Validate all necessary fields present
5. Save via UserManager
6. Return success/failure
```

**Interactions:**
- **HttpServletRequest**: Source of user attributes
- **User**: Creates new instances
- **UserManager**: Persists new users
- **AutoProvision**: Implements this interface

---

### 15. AuthoritiesPopulator

**Package:** `org.apache.roller.weblogger.ui.core.security`

**Role:** LDAP authorities populator for Spring Security.

**Description:**
`AuthoritiesPopulator` implements Spring Security's `LdapAuthoritiesPopulator` to assign authorities (roles) to users authenticated via LDAP. It maps LDAP groups/attributes to Roller roles.

**Interfaces Implemented:**
- `LdapAuthoritiesPopulator` (Spring Security LDAP)

**Methods:**

| Method | Description |
|--------|-------------|
| `getGrantedAuthorities(userData, username)` | Returns authorities for LDAP user |
| `setDefaultRole(defaultRole)` | Sets default role for all LDAP users |

**Interactions:**
- **DirContextOperations**: LDAP user context
- **GrantedAuthority**: Returns collection of authorities
- **Spring Security LDAP**: Called during LDAP authentication

---

### 16. CustomUserRegistry

**Package:** `org.apache.roller.weblogger.ui.core.security`

**Role:** Utility for extracting user details from authentication context.

**Description:**
`CustomUserRegistry` provides static utility methods to extract user information from various authentication sources (LDAP, CMA, database). It handles the complexity of different attribute names across authentication methods.

**LDAP Attribute Constants:**

| Constant | Default Value | Description |
|----------|---------------|-------------|
| `DEFAULT_SNAME_LDAP_ATTRIBUTE` | "screenName" | Screen name attribute |
| `DEFAULT_UID_LDAP_ATTRIBUTE` | "uid" | User ID attribute |
| `DEFAULT_NAME_LDAP_ATTRIBUTE` | "cn" | Full name attribute |
| `DEFAULT_EMAIL_LDAP_ATTRIBUTE` | "mail" | Email attribute |
| `DEFAULT_LOCALE_LDAP_ATTRIBUTE` | "locale" | Locale attribute |
| `DEFAULT_TIMEZONE_LDAP_ATTRIBUTE` | "timezone" | Timezone attribute |

**Methods:**

| Method | Description |
|--------|-------------|
| `getUserDetailsFromAuthentication(request)` | Extracts User from auth context |
| `getLdapAttribute(attributes, name)` | Gets LDAP attribute value |
| `getRequestAttribute(request, attributeName)` | Gets request attribute value |

**Interactions:**
- **HttpServletRequest**: Source for CMA attributes
- **Attributes**: LDAP attributes container
- **AuthMethod**: Determines extraction strategy
- **UserDetails/RollerUserDetails**: Type checking for sources
- **User**: Returns populated instance

---

### 17. RollerRememberMeServices

**Package:** `org.apache.roller.weblogger.ui.core.security`

**Role:** Custom remember-me authentication service.

**Description:**
`RollerRememberMeServices` extends Spring Security's `TokenBasedRememberMeServices` to provide Roller-specific token generation. This enables persistent login across browser sessions.

**Extends:** `TokenBasedRememberMeServices`

**Methods:**

| Method | Description |
|--------|-------------|
| `makeTokenSignature(expireTime, username, password)` | Creates secure token |

**Interactions:**
- **RollerUserDetailsService**: Loads user for token validation
- **UserDetailsService**: Required by parent class
- **Spring Security**: Integrated into security filter chain

---

## UI Layer - Struts2 Actions

### 18. UIAction (Abstract)

**Package:** `org.apache.roller.weblogger.ui.struts2.util`

**Role:** Base class for all Struts2 actions in Roller.

**Description:**
`UIAction` provides common functionality for all web actions including security declarations, user/weblog context, and standard action methods. All Roller actions extend this class.

**Extends:** `ActionSupport` (Struts2)

**Implements:**
- `RequestAware`: Access to HTTP request
- `UISecurityEnforced`: Security declarations
- `UIActionPreparable`: Pre-action preparation

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `authenticatedUser` | User | Currently logged-in user |
| `actionWeblog` | Weblog | Current weblog context |
| `weblog` | String | Weblog handle from request |
| `actionName` | String | Current action name |
| `desiredMenu` | String | Menu to highlight |
| `pageTitle` | String | Page title for display |
| `salt` | String | CSRF token |

**Security Methods:**

| Method | Description |
|--------|-------------|
| `isUserRequired()` | Returns true if login required (default: true) |
| `isWeblogRequired()` | Returns true if weblog context required (default: false) |
| `requiredWeblogPermissionActions()` | Required weblog permissions |
| `requiredGlobalPermissionActions()` | Required global permissions |

**Interactions:**
- **User**: Holds authenticated user reference
- **Weblog**: Holds current weblog context
- **UISecurityInterceptor**: Checks security declarations
- **UIActionInterceptor**: Prepares action context
- **All action classes**: Inherit from this

---

### 19. UISecurityEnforced (Interface)

**Package:** `org.apache.roller.weblogger.ui.struts2.util`

**Role:** Interface for declaring action security requirements.

**Description:**
`UISecurityEnforced` defines methods that actions implement to declare their security requirements. The `UISecurityInterceptor` uses these declarations to enforce access control.

**Methods:**

| Method | Description |
|--------|-------------|
| `isUserRequired()` | Whether authenticated user required |
| `isWeblogRequired()` | Whether weblog context required |
| `requiredWeblogPermissionActions()` | List of required weblog permissions |
| `requiredGlobalPermissionActions()` | List of required global permissions |

**Interactions:**
- **UIAction**: Implements this interface
- **UISecurityInterceptor**: Reads declarations
- **All actions**: Override to specify requirements

---

### 20. UIActionPreparable (Interface)

**Package:** `org.apache.roller.weblogger.ui.struts2.util`

**Role:** Interface for action preparation logic.

**Description:**
`UIActionPreparable` provides a hook for actions to perform initialization before execution. This is called by `UIActionInterceptor` after security checks pass.

**Methods:**

| Method | Description |
|--------|-------------|
| `myPrepare()` | Called before action execution |

**Interactions:**
- **UIAction**: Implements this interface
- **UIActionInterceptor**: Calls myPrepare()

---

### 21. UISecurityInterceptor

**Package:** `org.apache.roller.weblogger.ui.struts2.util`

**Role:** Struts2 interceptor for enforcing action security.

**Description:**
`UISecurityInterceptor` is a Struts2 interceptor that enforces access control before actions execute. It checks the security declarations from `UISecurityEnforced` against the current user's permissions.

**Extends:** `MethodFilterInterceptor` (Struts2)

**Methods:**

| Method | Description |
|--------|-------------|
| `doIntercept(invocation)` | Performs security checks |

**Security Check Flow:**

```
1. Get action from invocation
2. Cast to UISecurityEnforced
3. If isUserRequired() and no user  redirect to login
4. If requiredGlobalPermissionActions() specified:
   - Create GlobalPermission with required actions
   - Check via UserManager.checkPermission()
   - Deny if check fails
5. If isWeblogRequired() and no weblog  error
6. If requiredWeblogPermissionActions() specified:
   - Create WeblogPermission with required actions
   - Check via UserManager.checkPermission()
   - Deny if check fails
7. Proceed to action if all checks pass
```

**Interactions:**
- **UISecurityEnforced**: Reads security declarations
- **ActionInvocation**: Controls action execution flow
- **UserManager**: Performs permission checks
- **GlobalPermission/WeblogPermission**: Creates for checks

---

### 22. UIActionInterceptor

**Package:** `org.apache.roller.weblogger.ui.struts2.util`

**Role:** Struts2 interceptor for action preparation.

**Description:**
`UIActionInterceptor` prepares the action context by loading the authenticated user and weblog context. It calls `myPrepare()` on actions that implement `UIActionPreparable`.

**Extends:** `MethodFilterInterceptor` (Struts2)

**Implements:** `StrutsStatics`

**Methods:**

| Method | Description |
|--------|-------------|
| `doIntercept(invocation)` | Prepares action context |

**Preparation Flow:**

```
1. Get action from invocation
2. Load authenticated user from RollerSession
3. Set authenticatedUser on UIAction
4. If weblog parameter present:
   - Load Weblog by handle
   - Set actionWeblog on UIAction
5. Call myPrepare() if action implements UIActionPreparable
6. Proceed to action execution
```

**Interactions:**
- **ActionInvocation**: Access to action and context
- **UIAction**: Sets user and weblog context
- **RollerSession**: Gets authenticated user
- **UIActionPreparable**: Calls myPrepare()
- **StrutsStatics**: Access to Struts context keys

---

### 23. UserAdmin

**Package:** `org.apache.roller.weblogger.ui.struts2.admin`

**Role:** Action for admin user list view.

**Description:**
`UserAdmin` displays the list of users for administrators. It requires global admin permission and provides user search and filtering capabilities.

**Extends:** `UIAction`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `bean` | CreateUserBean | Form data bean |
| `authMethod` | AuthMethod | Current authentication method |

**Methods:**

| Method | Description |
|--------|-------------|
| `execute()` | Displays user list |
| `edit()` | Redirects to user edit |
| `requiredGlobalPermissionActions()` | Returns ["admin"] |
| `isWeblogRequired()` | Returns false |

**Interactions:**
- **CreateUserBean**: Holds form/search data
- **AuthMethod**: Determines UI options
- **UserManager**: Fetches user list
- **UIAction**: Inherits common functionality

---

### 24. UserEdit

**Package:** `org.apache.roller.weblogger.ui.struts2.admin`

**Role:** Action for creating and editing users (admin).

**Description:**
`UserEdit` handles the create/edit user form for administrators. It supports creating new users, editing existing users, and managing user permissions and roles.

**Extends:** `UIAction`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `bean` | CreateUserBean | Form data bean |
| `authMethod` | AuthMethod | Current authentication method |

**Methods:**

| Method | Description |
|--------|-------------|
| `myPrepare()` | Loads user data for edit |
| `execute()` | Displays edit form |
| `firstSave()` | Creates new user |
| `save()` | Updates existing user |
| `isAdd()` | True if creating new user |
| `myValidate()` | Validates form data |
| `isUserEditingSelf()` | True if admin editing own account |
| `getPermissions()` | Gets user's weblog permissions |
| `requiredGlobalPermissionActions()` | Returns ["admin"] |

**Validation Rules:**
- Username required and unique (for new users)
- Email required and valid format
- Password required for new users (database auth)
- Password confirmation must match

**Interactions:**
- **CreateUserBean**: Data transfer object
- **User**: Creates/updates via UserManager
- **UserManager**: All CRUD operations
- **WeblogPermission**: Displays user's permissions
- **AuthMethod**: Determines password requirements

---

### 25. CreateUserBean

**Package:** `org.apache.roller.weblogger.ui.struts2.admin`

**Role:** Data transfer object for user create/edit forms.

**Description:**
`CreateUserBean` is a form bean that transfers data between the user edit UI and the User entity. It provides methods to copy data to and from User objects.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | User ID |
| `userName` | String | Login username |
| `password` | String | New password (edit only) |
| `screenName` | String | Display name |
| `fullName` | String | Full name |
| `emailAddress` | String | Email address |
| `locale` | String | Preferred locale |
| `timeZone` | String | Preferred timezone |
| `openIdUrl` | String | OpenID URL |
| `enabled` | Boolean | Account enabled |
| `activationCode` | String | Email activation code |
| `administrator` | Boolean | Admin role flag |
| `list` | List<String> | Additional data |

**Methods:**

| Method | Description |
|--------|-------------|
| `copyTo(user)` | Copies bean values to User entity |
| `copyFrom(user)` | Copies User entity values to bean |

**Interactions:**
- **User**: Bidirectional data transfer
- **UserAdmin/UserEdit**: Uses for form binding

---

### 26. Register

**Package:** `org.apache.roller.weblogger.ui.struts2.core`

**Role:** Action for new user self-registration.

**Description:**
`Register` handles the user registration workflow including form display, validation, account creation, and email activation. Registration can be disabled system-wide.

**Extends:** `UIAction`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `DISABLED_RETURN_CODE` | String (static) | Return code when registration disabled |
| `DEFAULT_ALLOWED_CHARS` | String (static) | Valid username characters |
| `authMethod` | AuthMethod | Current authentication method |
| `activationStatus` | String | Email activation status |
| `bean` | ProfileBean | Registration form data |
| `activationCode` | String | Email verification code |

**Methods:**

| Method | Description |
|--------|-------------|
| `execute()` | Displays registration form |
| `save()` | Processes registration |
| `activate()` | Handles email activation |
| `retryActivationCode(mgr, code)` | Resends activation email |
| `sendActivationMailIfNeeded(user, enabled)` | Sends activation email |
| `myValidate()` | Validates registration data |
| `checkUserName()` | Validates username format/availability |
| `checkOpenID()` | Validates OpenID URL |
| `isUserRequired()` | Returns false (public page) |

**Registration Flow:**

```
1. User accesses registration form (execute)
2. User submits form (save)
3. Validation runs (myValidate, checkUserName, checkOpenID)
4. If valid, create User via UserManager
5. If email activation enabled:
   - Generate activation code
   - Send activation email
   - Return "activationSent" result
6. If no activation:
   - Enable account immediately
   - Return "success" result
7. User clicks activation link (activate)
8. Verify activation code
9. Enable account
```

**Interactions:**
- **ProfileBean**: Form data
- **UserManager**: Creates user, saves activation code
- **AuthMethod**: Determines password/OpenID requirements
- **Mail System**: Sends activation emails

---

### 27. Login

**Package:** `org.apache.roller.weblogger.ui.struts2.core`

**Role:** Action for login page display.

**Description:**
`Login` displays the login form. Actual authentication is handled by Spring Security; this action only manages the UI and error display.

**Extends:** `UIAction`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `error` | String | Error message from failed login |
| `authMethod` | AuthMethod | Current authentication method |

**Methods:**

| Method | Description |
|--------|-------------|
| `execute()` | Displays login form |
| `isUserRequired()` | Returns false (public page) |
| `getAuthMethod()` | Returns current auth method |

**Interactions:**
- **AuthMethod**: Determines login options (OpenID, LDAP, etc.)
- **Spring Security**: Handles actual authentication
- **UIAction**: Inherits common functionality

---

### 28. Profile

**Package:** `org.apache.roller.weblogger.ui.struts2.core`

**Role:** Action for user profile editing (self-service).

**Description:**
`Profile` allows authenticated users to edit their own profile information. Unlike `UserEdit`, this is for regular users editing themselves, not admin editing others.

**Extends:** `UIAction`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `authMethod` | AuthMethod | Current authentication method |
| `bean` | ProfileBean | Profile form data |

**Methods:**

| Method | Description |
|--------|-------------|
| `execute()` | Displays profile form |
| `save()` | Saves profile changes |
| `myValidate()` | Validates profile data |

**Editable Fields:**
- Screen name
- Full name
- Email address
- Locale
- Timezone
- Password (database auth only)

**Interactions:**
- **ProfileBean**: Form data
- **User**: Updates via UserManager
- **UserManager**: Save operations
- **AuthMethod**: Determines editable fields

---

### 29. MainMenu

**Package:** `org.apache.roller.weblogger.ui.struts2.core`

**Role:** Action for user's main dashboard.

**Description:**
`MainMenu` displays the user's main menu/dashboard showing their weblogs and pending invitations. Users can accept or decline weblog invitations from here.

**Extends:** `UIAction`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `webSiteId` | String | Weblog ID for accept/decline |
| `inviteId` | String | Invitation ID |

**Methods:**

| Method | Description |
|--------|-------------|
| `execute()` | Displays main menu |
| `accept()` | Accepts weblog invitation |
| `decline()` | Declines weblog invitation |
| `getExistingPermissions()` | Gets user's current permissions |
| `getPendingPermissions()` | Gets user's pending invitations |

**Interactions:**
- **WeblogPermission**: Displays and manages
- **UserManager**: Accept/decline operations
- **UIAction**: Inherits common functionality

---

### 30. Members

**Package:** `org.apache.roller.weblogger.ui.struts2.editor`

**Role:** Action for managing weblog members.

**Description:**
`Members` displays the list of weblog members and allows changing their permissions or removing them. Requires admin permission on the weblog.

**Extends:** `UIAction`

**Implements:** `HttpParametersAware`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `allPermissions` | List<WeblogPermission> | All weblog members |
| `parameters` | HttpParameters | Request parameters |

**Methods:**

| Method | Description |
|--------|-------------|
| `execute()` | Displays member list |
| `save()` | Saves permission changes |
| `getWeblogPermissions()` | Gets all members |
| `getParameter(key)` | Gets request parameter |
| `setParameters(params)` | Sets parameters from Struts |

**Interactions:**
- **WeblogPermission**: Manages member permissions
- **UserManager**: Permission CRUD operations
- **UIAction**: Inherits common functionality
- **HttpParametersAware**: Access to request parameters

---

### 31. MembersInvite

**Package:** `org.apache.roller.weblogger.ui.struts2.editor`

**Role:** Action for inviting users to a weblog.

**Description:**
`MembersInvite` handles the invitation workflow for adding members to a weblog. Creates pending permissions that users must accept.

**Extends:** `UIAction`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `userName` | String | Username to invite |
| `permissionString` | String | Permissions to grant |

**Methods:**

| Method | Description |
|--------|-------------|
| `execute()` | Displays invite form |
| `save()` | Sends invitation |
| `cancel()` | Cancels and returns |

**Invitation Flow:**

```
1. Admin enters username and permission level
2. System validates user exists
3. Creates pending WeblogPermission
4. (Optional) Sends notification email
5. Invited user sees in MainMenu pending list
6. User accepts or declines
```

**Interactions:**
- **UserManager**: Validates user, creates pending permission
- **WeblogPermission**: Created as pending
- **UIAction**: Inherits common functionality

---

### 32. MemberResign

**Package:** `org.apache.roller.weblogger.ui.struts2.editor`

**Role:** Action for users to resign from a weblog.

**Description:**
`MemberResign` allows users to voluntarily remove themselves from a weblog's member list. Unlike being removed by admin, this is user-initiated.

**Extends:** `UIAction`

**Methods:**

| Method | Description |
|--------|-------------|
| `execute()` | Displays confirmation page |
| `resign()` | Processes resignation |
| `requiredWeblogPermissionActions()` | Returns minimal permission |
| `isWeblogRequired()` | Returns true |

**Interactions:**
- **UserManager**: Revokes user's weblog permission
- **WeblogPermission**: Removes user's permission
- **UIAction**: Inherits common functionality

---

## Session Management

### 33. RollerSession

**Package:** `org.apache.roller.weblogger.ui.core`

**Role:** HTTP session wrapper for authenticated user context.

**Description:**
`RollerSession` wraps HTTP session functionality and provides easy access to the authenticated user. It implements session listeners to clean up on session destruction or passivation.

**Implements:**
- `HttpSessionListener`: Session lifecycle events
- `HttpSessionActivationListener`: Session passivation/activation

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `serialVersionUID` | long (static) | Serialization version |
| `userName` | String | Authenticated username |
| `ROLLER_SESSION` | String (static) | Session attribute key |

**Methods:**

| Method | Description |
|--------|-------------|
| `getRollerSession(request)` | Gets/creates RollerSession from request |
| `sessionCreated(event)` | Handles session creation |
| `sessionDestroyed(event)` | Handles session destruction |
| `sessionWillPassivate(event)` | Handles session passivation |
| `getAuthenticatedUser()` | Returns authenticated User object |
| `setAuthenticatedUser(user)` | Sets authenticated User |
| `clearSession()` | Clears session data |

**Interactions:**
- **User**: Stores authenticated user reference
- **HttpServletRequest**: Gets session from request
- **RollerLoginSessionManager**: Registers/invalidates sessions
- **UIActionInterceptor**: Gets authenticated user

---

### 34. RollerLoginSessionManager

**Package:** `org.apache.roller.weblogger.ui.core`

**Role:** Singleton manager for active user sessions.

**Description:**
`RollerLoginSessionManager` tracks all active user sessions, enabling features like session invalidation across multiple logins and concurrent session control.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `CACHE_ID` | String (static) | Cache identifier |
| `sessionCache` | Cache | Session storage cache |

**Methods:**

| Method | Description |
|--------|-------------|
| `getInstance()` | Returns singleton instance |
| `register(userName, session)` | Registers new session |
| `get(userName)` | Gets session by username |
| `invalidate(userName)` | Invalidates user's session |

**Use Cases:**
- Force logout when admin disables account
- Limit concurrent sessions per user
- Session tracking for security audit

**Interactions:**
- **RollerSession**: Stores references
- **Cache**: Underlying storage mechanism
- **Security components**: Session management operations

---

### 35. ProfileBean

**Package:** `org.apache.roller.weblogger.ui.core`

**Role:** Data transfer object for profile forms.

**Description:**
`ProfileBean` is a form bean for user profile editing, used by both `Register` and `Profile` actions. Similar to `CreateUserBean` but includes password confirmation fields.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | User ID |
| `userName` | String | Username |
| `password` | String | Stored password hash |
| `screenName` | String | Display name |
| `fullName` | String | Full name |
| `emailAddress` | String | Email address |
| `locale` | String | Preferred locale |
| `timeZone` | String | Preferred timezone |
| `openidUrl` | String | OpenID URL |
| `passwordText` | String | New password (plain text) |
| `passwordConfirm` | String | Password confirmation |

**Methods:**

| Method | Description |
|--------|-------------|
| `copyTo(user)` | Copies bean values to User entity |
| `copyFrom(user)` | Copies User entity values to bean |

**Interactions:**
- **User**: Bidirectional data transfer
- **Register/Profile**: Uses for form binding

---

## Caching

### 36. Cache (Interface)

**Package:** `org.apache.roller.weblogger.util.cache`

**Role:** Generic caching interface.

**Description:**
`Cache` defines a simple caching contract used throughout Roller. The `RollerLoginSessionManager` uses this for session storage.

**Methods:**

| Method | Description |
|--------|-------------|
| `getId()` | Returns cache identifier |
| `put(key, value)` | Stores value with key |
| `get(key)` | Retrieves value by key |
| `remove(key)` | Removes entry by key |
| `getStats()` | Returns cache statistics |

**Interactions:**
- **RollerLoginSessionManager**: Uses for session cache

---

## Configuration

### 37. AuthMethod (Enum)

**Package:** `org.apache.roller.weblogger.config`

**Role:** Enumeration of supported authentication methods.

**Description:**
`AuthMethod` defines the available authentication methods in Roller. The configured method affects UI behavior, user provisioning, and security configuration.

**Values:**

| Value | Description |
|-------|-------------|
| `ROLLERDB` | Database authentication (username/password stored locally) |
| `LDAP` | LDAP/Active Directory authentication |
| `OPENID` | OpenID authentication only |
| `DB_OPENID` | Database or OpenID authentication (user's choice) |
| `CMA` | Container Managed Authentication |

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `propertyName` | String | Configuration property name |

**Methods:**

| Method | Description |
|--------|-------------|
| `getPropertyName()` | Returns property name |
| `getAuthMethod()` | Static method to get configured method |

**UI Impact by Auth Method:**

| Auth Method | Password Fields | OpenID Fields | User Provisioning |
|-------------|-----------------|---------------|-------------------|
| ROLLERDB | Required | Hidden | Manual only |
| LDAP | Hidden | Hidden | Auto-provision |
| OPENID | Hidden | Required | Manual only |
| DB_OPENID | Optional | Optional | Manual only |
| CMA | Hidden | Hidden | Auto-provision |

**Interactions:**
- **Login/Register/Profile**: Adjust UI based on auth method
- **UserEdit/UserAdmin**: Determine editable fields
- **CustomUserRegistry**: Determines extraction strategy
- **RollerUserDetailsService**: Affects authentication flow

---

## Summary

The User and Role Management Subsystem is a comprehensive implementation that:

1. **Supports multiple authentication methods** through the `AuthMethod` enum and flexible security components
2. **Implements hierarchical permissions** with `GlobalPermission` and `WeblogPermission`
3. **Uses clean layered architecture** separating UI, business logic, and persistence
4. **Integrates with Spring Security** for authentication and remember-me functionality
5. **Provides admin and self-service interfaces** through Struts2 actions
6. **Manages sessions** with `RollerSession` and `RollerLoginSessionManager`
7. **Enforces security declaratively** through `UISecurityEnforced` and interceptors

The subsystem demonstrates good software engineering practices including:
- Interface-based programming (`UserManager` interface)
- Dependency injection (JPA strategy injection)
- Separation of concerns (clear layer boundaries)
- Template Method pattern (UIAction with hooks)
- Interceptor pattern (Struts2 interceptors for cross-cutting concerns)


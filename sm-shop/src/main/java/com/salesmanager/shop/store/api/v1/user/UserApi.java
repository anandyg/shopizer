package com.salesmanager.shop.store.api.v1.user;

import java.security.Principal;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.ImmutableMap;
import com.salesmanager.core.model.common.Criteria;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.entity.EntityExists;
import com.salesmanager.shop.model.entity.UniqueEntity;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.model.user.ReadableUserList;
import com.salesmanager.shop.model.user.UserPassword;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.store.facade.StoreFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;
import com.salesmanager.shop.utils.ServiceRequestCriteriaBuilderUtils;

import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import springfox.documentation.annotations.ApiIgnore;

/** Api for managing admin users */
@RestController
@RequestMapping(value = "/api/v1")
public class UserApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserApi.class);

  @Inject
  private StoreFacade storeFacade;

  @Inject
  private UserFacade userFacade;


  // mapping between readable field name and backend field name
  private static final Map<String, String> MAPPING_FIELDS = ImmutableMap.<String, String>builder()
      .put("emailAddress", "adminEmail").put("userName", "adminName").build();

  /**
   * Get userName by merchant code and userName
   *
   * @param storeCode
   * @param name
   * @param request
   * @return
   */
  @ResponseStatus(HttpStatus.OK)
  @GetMapping({"/private/users/{id}"})
  @ApiOperation(httpMethod = "GET", value = "Get a specific user profile by user id", notes = "",
      produces = MediaType.APPLICATION_JSON_VALUE, response = ReadableUser.class)
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success", responseContainer = "User",
          response = ReadableUser.class),
      @ApiResponse(code = 400, message = "Error while getting User"),
      @ApiResponse(code = 401, message = "Login required")})
  @ApiImplicitParams({
      @ApiImplicitParam(name = "store", dataType = "string", defaultValue = "DEFAULT"),
      @ApiImplicitParam(name = "lang", dataType = "string", defaultValue = "en")})
  public ReadableUser get(@ApiIgnore MerchantStore merchantStore, @ApiIgnore Language language,
      @PathVariable Long id, HttpServletRequest request) {
    return userFacade.findById(id, merchantStore.getCode(), language);
  }

  /**
   * Creates a new user
   *
   * @param store
   * @param user
   * @return
   */
  @ResponseStatus(HttpStatus.OK)
  //@PostMapping(value = {"/private/{store}/user/", "/private/user/"},
  //    produces = MediaType.APPLICATION_JSON_VALUE)
  @PostMapping(value = {"/private/user/"},
  produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiOperation(httpMethod = "POST", value = "Creates a new user", notes = "",
      response = ReadableUser.class)
  @ApiImplicitParams({
	    @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
	    @ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en")})
  public ReadableUser create(
      @ApiIgnore MerchantStore merchantStore,
      @ApiIgnore Language language,
      @Valid @RequestBody PersistableUser user, 
      HttpServletRequest request) {
    /** Must be superadmin or admin */
    String authenticatedUser = userFacade.authenticatedUser();
    if (authenticatedUser == null) {
      throw new UnauthorizedException();
    }
    // only admin and superadmin allowed
    userFacade.authorizedGroup(authenticatedUser,
        Stream.of("SUPERADMIN", "ADMIN")
        .collect(Collectors.toList()));
    
    userFacade.authorizedGroups(authenticatedUser, user);

    MerchantStore store = storeFacade.get(merchantStore.getCode());

    /** if user is admin, user must be in that store */
    if (!request.isUserInRole("SUPERADMIN")) {
      if(!userFacade.authorizedStore(authenticatedUser, store.getCode())) {
    	  throw new UnauthorizedException("Operation unauthorized for user [" + authenticatedUser + "] and store [" + merchantStore.getCode() + "]");
      }
    }
    

    return userFacade.create(user, merchantStore);
  }

  @ResponseStatus(HttpStatus.OK)
  //@PutMapping(value = {"/private/{store}/user/{id}","/private/user/{id}"}, produces = MediaType.APPLICATION_JSON_VALUE)
  @PutMapping(value = {"/private/user/{id}"}, produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiImplicitParams({
	    @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
	    @ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en")})
  @ApiOperation(httpMethod = "PUT", value = "Updates a user", notes = "",
      response = ReadableUser.class)
  public ReadableUser update(
      @Valid @RequestBody PersistableUser user, 
      @PathVariable Long id,
      @ApiIgnore MerchantStore merchantStore,
      @ApiIgnore Language language

      ) {


    String authenticatedUser = userFacade.authenticatedUser();//requires user doing action
    
    userFacade.authorizedGroups(authenticatedUser, user);


    return userFacade.update(id, authenticatedUser, merchantStore.getCode(), user);
  }

  @ResponseStatus(HttpStatus.OK)
  @PatchMapping(value = {"/private/user/{id}/password"}, produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiOperation(httpMethod = "PATCH", value = "Updates a user password", notes = "",
      response = Void.class)
  public void password(
      @Valid @RequestBody UserPassword password, 
      @PathVariable Long id) {

    String authenticatedUser = userFacade.authenticatedUser();
    if (authenticatedUser == null) {
      throw new UnauthorizedException();
    }
    userFacade.changePassword(id, authenticatedUser, password);
  }

  @ResponseStatus(HttpStatus.OK)
  @GetMapping(value = {"/private/users"},
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiOperation(httpMethod = "GET", value = "Get list of user", notes = "",
      response = ReadableUserList.class)
  @ApiImplicitParams({
	    @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
	    @ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en")})
  public ReadableUserList list(
	  @ApiIgnore MerchantStore merchantStore,
	  @ApiIgnore Language language,
      @RequestParam(value = "page", required = false, defaultValue="0") Integer page,
      @RequestParam(value = "count", required = false, defaultValue="10") Integer count,
      HttpServletRequest request) {


    String authenticatedUser = userFacade.authenticatedUser();
    if (authenticatedUser == null) {
      throw new UnauthorizedException();
    }

    Criteria criteria = createCriteria(request);
    criteria.setStoreCode(merchantStore.getCode());
    
    if (request.isUserInRole("SUPERADMIN")) {
    	criteria.setStoreCode(null);
    }

    if (!request.isUserInRole("SUPERADMIN")) {
      if(!userFacade.authorizedStore(authenticatedUser, merchantStore.getCode())) {
    	  throw new UnauthorizedException("Operation unauthorized for user [" + authenticatedUser + "] and store [" + merchantStore + "]");
      }
    }

    userFacade.authorizedGroup(authenticatedUser,
        Stream.of("SUPERADMIN", "ADMIN").collect(Collectors.toList()));

    return userFacade.listByCriteria(criteria, page, count, language);
  }

  @ResponseStatus(HttpStatus.OK)
  @DeleteMapping(value = {"/private/user/{id}"})
  @ApiOperation(httpMethod = "DELETE", value = "Deletes a user", notes = "", response = Void.class)
  @ApiImplicitParams({
      @ApiImplicitParam(name = "store", dataType = "string", defaultValue = "DEFAULT"),
      @ApiImplicitParam(name = "lang", dataType = "string", defaultValue = "en")})
  public void delete(@ApiIgnore MerchantStore merchantStore, @ApiIgnore Language language,
      @PathVariable Long id, HttpServletRequest request) {

    /** Must be superadmin or admin */
    String authenticatedUser = userFacade.authenticatedUser();
    if (authenticatedUser == null) {
      throw new UnauthorizedException();
    }

    if (!request.isUserInRole("SUPERADMIN")) {
      userFacade.authorizedStore(authenticatedUser, merchantStore.getCode());
    }

    userFacade.authorizedGroup(authenticatedUser,
        Stream.of("SUPERADMIN", "ADMIN").collect(Collectors.toList()));

    userFacade.delete(id, merchantStore.getCode());
  }

  @ResponseStatus(HttpStatus.OK)
  @PostMapping(value = {"/private/user/unique"}, produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiOperation(httpMethod = "POST", value = "Check if username already exists", notes = "",
      response = EntityExists.class)
  public ResponseEntity<EntityExists> exists(@ApiIgnore MerchantStore merchantStore,
      @ApiIgnore Language language, @RequestBody UniqueEntity userName) {

    boolean isUserExist = true;// default user exist
    try {
      // will throw an exception if not fount
      userFacade.findByUserName(userName.getUnique(), userName.getMerchant(), language);

    } catch (ResourceNotFoundException e) {
      isUserExist = false;
    }
    return new ResponseEntity<EntityExists>(new EntityExists(isUserExist), HttpStatus.OK);
  }

  private Criteria createCriteria(HttpServletRequest request) {
    Criteria criteria = ServiceRequestCriteriaBuilderUtils.buildRequest(MAPPING_FIELDS, request);

    //Optional.ofNullable(start).ifPresent(criteria::setStartIndex);
    //Optional.ofNullable(count).ifPresent(criteria::setMaxCount);

    return criteria;
  }

  
  /**
   * Get logged in customer profile
   * @param merchantStore
   * @param language
   * @param request
   * @return
   */
  @GetMapping("/private/user/profile")
  @ApiImplicitParams({
      @ApiImplicitParam(name = "lang", dataType = "string", defaultValue = "en")
  })
  public ReadableUser getAuthUser(
      @ApiIgnore Language language,
      HttpServletRequest request) {
    Principal principal = request.getUserPrincipal();
    String userName = principal.getName();
    return userFacade.findByUserName(userName, null, language);
    

  }
}

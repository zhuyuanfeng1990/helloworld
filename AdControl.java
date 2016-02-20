package com.stoneaxe.jiayijia.control;

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.stoneaxe.jiayijia.common.FileUtils;
import com.stoneaxe.jiayijia.common.JsonMapper;
import com.stoneaxe.jiayijia.common.JsonUtils;
import com.stoneaxe.jiayijia.common.TokenManager;
import com.stoneaxe.jiayijia.control.message.AdMessage;
import com.stoneaxe.jiayijia.control.message.BaseMessage;
import com.stoneaxe.jiayijia.dao.base.Page;
import com.stoneaxe.jiayijia.entity.Ad;
import com.stoneaxe.jiayijia.entity.User;
import com.stoneaxe.jiayijia.service.AdService;

@Controller
public class AdControl {
	private static final Logger logger = LoggerFactory.getLogger(AdControl.class);

	@Autowired
	private AdService adService;
	
	@RequestMapping(value = "/ad/view")
	public String view(ModelMap model, Long id, String descContent){
		
		model.put("descContent", descContent);
		return "/webs/adView";
	}
	
	@RequestMapping(value = "/ad/list")
	public String list(ModelMap model, String pageNo, Ad ad){
		Page<Ad> page = new Page<Ad>(10);
		page.setPageNo(StringUtils.isNotEmpty(pageNo)?Integer.parseInt(pageNo):1);
		adService.findAllAdsByPage(page, ad);
		
		model.put("page", page);
		model.put("pageId", "ad");
		return "/sys/ad/adList";
	}
	
	@RequestMapping(value = "/ad/create")
	public String createForm(ModelMap model){
		model.put("pageId", "ad");
		return "/sys/ad/adForm";
	}	
	
	@RequestMapping(value = "/ad/update")
	public String updateForm(HttpServletRequest request, ModelMap model, Long id){
		Ad ad = adService.getAd(id);
		String htmlContent = ad.getHtmlContent();
		if(StringUtils.isNotEmpty(ad.getHtmlContent())){
			htmlContent = htmlContent.replace(" src=\"", " src=\""+request.getContextPath());
			logger.debug("context Path：" + request.getContextPath());
			ad.setHtmlContent(htmlContent);
		}
		model.put("ad", ad);
		model.put("pageId", "ad");
		return "/sys/ad/adForm";
	}
	
	@RequestMapping(value = "/ad/save")
	public String save(HttpServletRequest request, ModelMap model, Long id, Ad ad, @RequestParam MultipartFile imgFile){
		String REAL_PATH = request.getSession().getServletContext().getRealPath("/");
		
		String code = "FAILED";
		String msg = "";
		try{
			if(id == null){
				//upload picture
				if(!imgFile.isEmpty()){
					String uploadUrl = FileUtils.uploadwithThumb(imgFile, REAL_PATH);
					ad.setAdPictureUrl(uploadUrl);
				}
				
				String htmlContent = ad.getHtmlContent();
				if(StringUtils.isNotEmpty(ad.getHtmlContent()) && request.getContextPath().length() > 1){
					htmlContent = htmlContent.replace(request.getContextPath(), "");
					ad.setHtmlContent(htmlContent);
				}
				ad.setUser(TokenManager.getWebUser());
				ad.setCreateDate(new Date());
				adService.saveAd(ad);
				code = "OK";
				msg = "添加广告成功";
			}else{
				Ad tmpAd = adService.getAd(id);
				tmpAd.setTitle(ad.getTitle());
				String htmlContent = ad.getHtmlContent();
				if(StringUtils.isNotEmpty(ad.getHtmlContent()) && request.getContextPath().length() > 1){
					htmlContent = htmlContent.replace(request.getContextPath(), "");
					tmpAd.setHtmlContent(htmlContent);
				}
				tmpAd.setHtmlUrl(ad.getHtmlUrl());
				//upload picture
				if(!imgFile.isEmpty()){
					String uploadUrl = FileUtils.uploadwithThumb(imgFile, REAL_PATH);
					tmpAd.setAdPictureUrl(uploadUrl);
				}
				
				adService.saveAd(tmpAd);
				ad = tmpAd;
				
				code = "OK";
				msg = "修改广告成功";
			}
		}catch (Exception e) {
			msg = "保存广告失败";
			logger.debug("error to save ad, ", e);
		}
		
		model.put("code", code);
		model.put("msg", msg);
		model.put("ad", ad);
		model.put("pageId", "ad");
		return "/sys/ad/adView";
	}
	
	/**
	 * 
	 * @param response
	 * @param ad ad中的adPictureUrl为预先上传，只需要保存url， 
	 * 				  htmlContent中也会有pic也要预先上传(/api/common/uploadPic), htmlContent的内容使用后台特定url访问，
	 * 							例如：/common/htmlView?type=ad&id=1, type可能为ad, noti, info
	 * 							htmlContent的构建，后台需要一个html editor来实现, App需要一个view list动态增加图片文字来实现
	 * 				  htmlUrl为访问ad的外部地址，为空时，后台提供servlet访问htmlContent
	 */
	@RequestMapping(value = "/api/ad/addAd")
	public void addAd(HttpServletResponse response, Ad ad){
		User currentUser = TokenManager.getCurrentUser(User.class);
		try{
			ad.setCreateDate(new Date());
			ad.setUser(currentUser);
			adService.saveAd(ad);
			JsonUtils.render(response, JsonMapper.buildNonEmptyMapper().toJson(new BaseMessage(true, "保存成功")));
		}catch(Exception e){
			logger.error("fail to add ad, ", e);
			JsonUtils.render(response, JsonMapper.buildNonEmptyMapper().toJson(new BaseMessage(false, "保存失败")));
		}
	}
	
	@RequestMapping(value = "/api/ad/deleteAd")
	public void deleteAd(HttpServletResponse response, long id){
		try{
			adService.deleteAd(id);
			JsonUtils.render(response, JsonMapper.buildNonEmptyMapper().toJson(new BaseMessage(true, "删除成功")));
		}catch(Exception e){
			logger.error("fail to delete ad, ", e);
			JsonUtils.render(response, JsonMapper.buildNonEmptyMapper().toJson(new BaseMessage(false, "删除失败")));
		}
	}
	
	@RequestMapping(value = "/api/ad/list")
	public void list(HttpServletResponse response){
		try{
			List<Ad> ads = adService.listTop3();
			JsonUtils.render(response, AdMessage.toJson(true, "查询成功", ads));
		}catch(Exception e){
			logger.error("fail to delete ad, ", e);
			JsonUtils.render(response, JsonMapper.buildNonEmptyMapper().toJson(new BaseMessage(false, "查询失败")));
		}
	}
}

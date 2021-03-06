
<#function zebra index>
  <#if (index % 2) == 0>
    <#return "even" />
  <#else>
    <#return "odd" />
  </#if>
</#function>

<#include "/html/header.ftl">

<#if errors??>
  <ul class="error">
    <#list errors as error>
      <li>${error}.</li>
    </#list>
  </ul>
</#if>

<p>Disk: <a href="${baseurl}disks/${uuid}">${uuid}</a></p>

<form action="${baseurl}disks/${uuid}/mounts/" enctype="application/x-www-form-urlencoded" method="POST">
  <table>
    <thead>
      <tr>
        <th></th>
        <th>VM ID</th>
        <th>Node</th>
        <th>Register Only?</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td>
          <input type="submit" value="Mount" />
        </td>
        <td>
          <input type="text" name="vm_id" size="10" value="" />
        </td>
        <td>
          <input type="text" name="node" size="40" value="" />
        </td>
        <td>
          <select name="register_only">
              <option selected="selected" value="false">false</option>
              <option value="true">true</option>
          </select>

        </td>
      </tr>
    </tbody>
  </table>
</form>

<hr/>
<br/>

<#if mounts?has_content>
	<table class="display">
	  <thead>
	    <tr>
	      <th>VM ID</th>
	      <th>Device</th>
	    </tr>
	  </thead>
	  <tbody>
	    <#list mounts as mount>
	      <tr class="${zebra(mount_index)}">
	        <td><a href="${baseurl}disks/${uuid}/mounts/${mount.id}/">${mount.vmId}</a></td>
	        <td class="center">${mount.device}</td>
	      </tr>
	    </#list>
	  </tbody>
	</table>
<#else>
  <p>No mounts.</p>
</#if>

<#include "/html/footer.ftl">

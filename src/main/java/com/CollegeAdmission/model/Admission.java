package com.CollegeAdmission.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class Admission
{

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long amd_no;
	@Column
	private String name;
	@Column
	private String gmail;
	@Column
	private String address;
	@Column
	private long mobile;
	public Admission() {
		super();
		// TODO Auto-generated constructor stub
	}
	public Admission(Long amd_no, String name, String gmail, String address, long mobile) {
		super();
		this.amd_no = amd_no;
		this.name = name;
		this.gmail = gmail;
		this.address = address;
		this.mobile = mobile;
	}
	public Long getAmd_no() {
		return amd_no;
	}
	public void setAmd_no(Long amd_no) {
		this.amd_no = amd_no;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getGmail() {
		return gmail;
	}
	public void setGmail(String gmail) {
		this.gmail = gmail;
	}
	public String getAddress() {
		return address;
	}
	public void setAddress(String address) {
		this.address = address;
	}
	public long getMobile() {
		return mobile;
	}
	public void setMobile(long mobile) {
		this.mobile = mobile;
	}
	

}

import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BreadcrumbsService } from '../../breadcrumbs/breadcrumbs.service';
import { ToolInfo } from '../tool.model';

@Component({
  selector: 'app-invocation-execution',
  templateUrl: './invocation-execution.component.html',
  styleUrls: ['./invocation-execution.component.css']
})
export class InvocationExecutionComponent implements OnInit {

  tool: ToolInfo = null

  constructor(private activatedRoute: ActivatedRoute, private breadcrumbService: BreadcrumbsService) {
    // let toolId = this.activatedRoute.snapshot.params['toolId'];
    for(let step of this.breadcrumbService.steps) {
      if(step.data.boutiquesToolInfo) {
        this.tool = step.data.boutiquesToolInfo;
        break;
      }
    }
  }

  ngOnInit() {
  }

}
